package ch.kekelidze.krakentrader.optimize;

import static io.jenetics.engine.Limits.bySteadyFitness;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import io.jenetics.DoubleChromosome;
import io.jenetics.DoubleGene;
import io.jenetics.Genotype;
import io.jenetics.Mutator;
import io.jenetics.Optimize;
import io.jenetics.SinglePointCrossover;
import io.jenetics.TournamentSelector;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.stat.DoubleMomentStatistics;
import io.jenetics.util.Factory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class MultiStrategyOptimizer implements Optimizer {

  private final BackTesterService backTesterService;
  private final StrategySelector strategySelector;
  private final HistoricalDataService historicalDataService;

  private static final double initialBalance = 1000;
  private static final int STEADY_FITNESS_GENERATIONS = 10;
  private static final int MAX_GENERATIONS = 15;

  // Available strategies to test
  private final List<String> availableStrategies = List.of(
      "multiIndexMomentum",
      "movingAverageScalper",
      "multiTimeFrameLowHigh",
      "supportResistanceConsolidation",
      "buyLowSellHighStrategy"
  );

  private final Map<String, OptimizationResult> bestResultsPerCoin = new ConcurrentHashMap<>();

  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    String coinPair = context.getSymbol();

    // Track the best results for this coin pair
    OptimizationResult bestResult = null;

    for (String strategyName : availableStrategies) {
      log.debug("Testing strategy {} for coin pair {}", strategyName, coinPair);

      // Create a fitness function specific to this strategy
      Engine<DoubleGene, Double> engine = Engine
          .builder(
              (Genotype<DoubleGene> genotype) -> fitnessFunction(coinPair, context.getPeriod(),
                  strategyName, context.getBars(), genotype),
              createGenotypeFactory())
          .populationSize(50)
          .selector(new TournamentSelector<>(3))
          .alterers(
              new Mutator<>(0.1),
              new SinglePointCrossover<>(0.6)
          )
          .optimize(Optimize.MAXIMUM)
          .build();

      final EvolutionStatistics<Double, DoubleMomentStatistics> statistics =
          EvolutionStatistics.ofNumber();
      Genotype<DoubleGene> bestGenotype = engine.stream()
          .limit(bySteadyFitness(STEADY_FITNESS_GENERATIONS))
          .limit(MAX_GENERATIONS)
          .peek(evolutionResult -> {
            statistics.accept(evolutionResult);
            log.debug("Statistics: {}", statistics);
          })
          .collect(EvolutionResult.toBestGenotype());

      // Extract the best parameters for this strategy
      StrategyParameters params = getStrategyParameters(bestGenotype);

      // Run a backtest with the optimized parameters to get the final result
      BacktestResult backtestResult = backTesterService.runSimulation(
          context, strategyName, params, initialBalance);

      double fitness = backtestResult.sharpeRatio();

      // Check if this strategy is better than previous ones
      if (bestResult == null || fitness > bestResult.fitness()) {
        bestResult = new OptimizationResult(strategyName, params, fitness);
      }

      log.debug("Strategy {} for {} achieved fitness: {}", strategyName, coinPair, fitness);
    }

    // Store the best results for this coin pair
    if (bestResult != null) {
      log.debug("Best strategy for {} is {} with fitness: {}",
          coinPair, bestResult.strategyName(), bestResult.fitness());
      bestResultsPerCoin.put(coinPair, bestResult);
      strategySelector.setBestStrategyForCoin(coinPair, bestResult.strategyName());
      return bestResult.parameters();
    } else {
      log.warn("No optimal strategy found for {}. Using default strategy.", coinPair);
      return StrategyParameters.builder().build(); // Default parameters
    }
  }

  private double fitnessFunction(String coinPair, int period, String strategyName, List<Bar> data,
      Genotype<DoubleGene> genotype) {
    // Extract parameters from genotype
    StrategyParameters params = getStrategyParameters(genotype);

    try {
      // Create context with appropriate data for this coin pair
      EvaluationContext context = EvaluationContext.builder()
          .symbol(coinPair)
          .period(period)
          .bars(data)
          .build();

      // Run simulation with the specific strategy and parameters
      BacktestResult result = backTesterService.runSimulation(
          context, strategyName, params, initialBalance);

      // Return Sharpe ratio as fitness
      return result.sharpeRatio();
    } catch (Exception e) {
      log.error("Error in fitness evaluation for {}, strategy {}: {}",
          coinPair, strategyName, e.getMessage());
      return -100.0; // Severely penalize failed evaluations
    }
  }

  // Create a genotype factory for parameter optimization
  private Factory<Genotype<DoubleGene>> createGenotypeFactory() {
    return Genotype.of(
        // Fast EMA period (5-50)
        DoubleChromosome.of(5, 50),

        // Slow EMA period (20-200)
        DoubleChromosome.of(20, 200),

        // RSI period (7-30)
        DoubleChromosome.of(7, 30),

        // RSI overbought threshold (65-85)
        DoubleChromosome.of(65, 85),

        // RSI oversold threshold (15-35)
        DoubleChromosome.of(15, 35),

        // MACD fast period (8-20)
        DoubleChromosome.of(8, 20),

        // MACD slow period (20-40)
        DoubleChromosome.of(20, 40),

        // MACD signal period (7-18)
        DoubleChromosome.of(7, 18),

        // ADX period (7-30)
        DoubleChromosome.of(7, 30),

        // ADX threshold (15-30)
        DoubleChromosome.of(15, 30),

        // Stop loss percentage (1-10, will be divided by 100)
        DoubleChromosome.of(1, 10),

        // Take profit percentage (2-20, will be divided by 100)
        DoubleChromosome.of(2, 20),

        // Volatility period (7-30)
        DoubleChromosome.of(7, 30),

        // Lookback period (10-100)
        DoubleChromosome.of(10, 100),

        // MFI period (7-30)
        DoubleChromosome.of(7, 30),

        // MFI overbought threshold (70-90)
        DoubleChromosome.of(70, 90),

        // MFI oversold threshold (10-30)
        DoubleChromosome.of(10, 30),

        // Support/Resistance period (20-200)
        DoubleChromosome.of(20, 200),

        // Support/Resistance threshold (0.01-0.05) [1% to 5%]
        DoubleChromosome.of(0.01, 0.05)
    );
  }

  private StrategyParameters getStrategyParameters(Genotype<DoubleGene> genotype) {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(genotype.get(0).get(0).intValue())
        .movingAverageBuyLongPeriod(genotype.get(1).get(0).intValue())
        .rsiPeriod(genotype.get(2).get(0).intValue())
        .rsiBuyThreshold(genotype.get(3).get(0).doubleValue())
        .rsiSellThreshold(genotype.get(4).get(0).doubleValue())
        .macdFastPeriod(genotype.get(5).get(0).intValue())
        .macdSlowPeriod(genotype.get(6).get(0).intValue())
        .macdSignalPeriod(genotype.get(7).get(0).intValue())
        .adxPeriod(genotype.get(8).get(0).intValue())
        .adxBullishThreshold(genotype.get(9).get(0).intValue())
        .adxBearishThreshold(genotype.get(9).get(0).intValue())
        .lossPercent(genotype.get(10).get(0).doubleValue())
        .profitPercent(genotype.get(11).get(0).doubleValue())
        .volatilityPeriod(genotype.get(12).get(0).intValue())
        .lookbackPeriod(genotype.get(13).get(0).intValue())
        .mfiPeriod(genotype.get(14).get(0).intValue())
        .mfiOverboughtThreshold(genotype.get(15).get(0).intValue())
        .mfiOversoldThreshold(genotype.get(16).get(0).intValue())
        .supportResistancePeriod(genotype.get(17).get(0).intValue())
        .supportResistanceThreshold(genotype.get(18).get(0).doubleValue())
        .aboveAverageThreshold(20)
        .volumePeriod(20)
        .minimumCandles(300)
        .build();
  }

  // Helper record to store optimization results
  private record OptimizationResult(
      String strategyName,
      StrategyParameters parameters,
      double fitness
  ) {

  }

  // Method to get a summary of best strategies for all coin pairs
  public Map<String, String> getBestStrategiesReport() {
    Map<String, String> report = new HashMap<>();

    bestResultsPerCoin.forEach((coinPair, result) -> {
      report.put(coinPair, String.format(
          "Strategy: %s, Fitness: %.4f",
          result.strategyName(),
          result.fitness()
      ));
    });

    return report;
  }
}