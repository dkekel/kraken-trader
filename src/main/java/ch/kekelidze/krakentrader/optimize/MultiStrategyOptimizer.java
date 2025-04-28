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

      double fitness = backtestResult.sharpeRatio() * (1 + backtestResult.winRate());

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
      return result.sharpeRatio() * (1 + result.winRate());
    } catch (Exception e) {
      log.error("Error in fitness evaluation for {}, strategy {}: {}",
          coinPair, strategyName, e.getMessage());
      return -100.0; // Severely penalize failed evaluations
    }
  }

  private Factory<Genotype<DoubleGene>> createGenotypeFactory() {
    return Genotype.of(
        // Moving average parameters
        DoubleChromosome.of(5, 50),   // movingAverageBuyShortPeriod
        DoubleChromosome.of(20, 200), // movingAverageBuyLongPeriod
        DoubleChromosome.of(5, 50),   // movingAverageSellShortPeriod
        DoubleChromosome.of(20, 200), // movingAverageSellLongPeriod

        // RSI parameters
        DoubleChromosome.of(7, 30),   // rsiPeriod
        DoubleChromosome.of(15, 35),  // rsiBuyThreshold
        DoubleChromosome.of(65, 85),  // rsiSellThreshold

        // MACD parameters
        DoubleChromosome.of(8, 20),   // macdFastPeriod
        DoubleChromosome.of(20, 40),  // macdSlowPeriod
        DoubleChromosome.of(7, 18),   // macdSignalPeriod

        // Volume parameters
        DoubleChromosome.of(10, 30),  // volumePeriod
        DoubleChromosome.of(1.0, 3.0), // aboveAverageThreshold

        // Loss/profit parameters
        DoubleChromosome.of(1, 10),   // lossPercent
        DoubleChromosome.of(2, 20),   // profitPercent

        // ADX parameters
        DoubleChromosome.of(7, 30),   // adxPeriod
        DoubleChromosome.of(15, 30),  // adxBullishThreshold
        DoubleChromosome.of(15, 30),  // adxBearishThreshold

        // Volatility parameters
        DoubleChromosome.of(7, 30),   // volatilityPeriod
        DoubleChromosome.of(0.01, 0.1), // contractionThreshold
        DoubleChromosome.of(0.01, 0.1), // lowVolatilityThreshold
        DoubleChromosome.of(0.1, 0.5),  // highVolatilityThreshold

        // MFI parameters
        DoubleChromosome.of(7, 30),   // mfiPeriod
        DoubleChromosome.of(70, 90),  // mfiOverboughtThreshold
        DoubleChromosome.of(10, 30),  // mfiOversoldThreshold

        // ATR parameters
        DoubleChromosome.of(7, 30),   // atrPeriod
        DoubleChromosome.of(1, 10),   // atrThreshold

        // Other parameters
        DoubleChromosome.of(10, 100), // lookbackPeriod
        DoubleChromosome.of(20, 200), // supportResistancePeriod
        DoubleChromosome.of(0.01, 0.05) // supportResistanceThreshold
    );
  }

  private StrategyParameters getStrategyParameters(Genotype<DoubleGene> genotype) {
    // Extract all period values to find the max
    int movingAverageBuyShortPeriod = genotype.get(0).get(0).intValue();
    int movingAverageBuyLongPeriod = genotype.get(1).get(0).intValue();
    int movingAverageSellShortPeriod = genotype.get(2).get(0).intValue();
    int movingAverageSellLongPeriod = genotype.get(3).get(0).intValue();
    int rsiPeriod = genotype.get(4).get(0).intValue();
    int macdFastPeriod = genotype.get(7).get(0).intValue();
    int macdSlowPeriod = genotype.get(8).get(0).intValue();
    int macdSignalPeriod = genotype.get(9).get(0).intValue();
    int volumePeriod = genotype.get(10).get(0).intValue();
    int adxPeriod = genotype.get(14).get(0).intValue();
    int volatilityPeriod = genotype.get(17).get(0).intValue();
    int mfiPeriod = genotype.get(21).get(0).intValue();
    int atrPeriod = genotype.get(24).get(0).intValue();
    int lookbackPeriod = genotype.get(26).get(0).intValue();
    int supportResistancePeriod = genotype.get(27).get(0).intValue();

    // Find the maximum period
    int maxPeriod = Math.max(
        Math.max(
            Math.max(
                Math.max(
                    Math.max(
                        Math.max(movingAverageBuyLongPeriod, movingAverageSellLongPeriod),
                        Math.max(rsiPeriod, macdSlowPeriod)
                    ),
                    Math.max(volumePeriod, adxPeriod)
                ),
                Math.max(volatilityPeriod, mfiPeriod)
            ),
            Math.max(atrPeriod, lookbackPeriod)
        ),
        supportResistancePeriod
    );

    int minimumCandles = Math.max(300, maxPeriod * 3);

    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(movingAverageBuyShortPeriod)
        .movingAverageBuyLongPeriod(movingAverageBuyLongPeriod)
        .movingAverageSellShortPeriod(movingAverageSellShortPeriod)
        .movingAverageSellLongPeriod(movingAverageSellLongPeriod)
        .rsiPeriod(rsiPeriod)
        .rsiBuyThreshold(genotype.get(5).get(0).doubleValue())
        .rsiSellThreshold(genotype.get(6).get(0).doubleValue())
        .macdFastPeriod(macdFastPeriod)
        .macdSlowPeriod(macdSlowPeriod)
        .macdSignalPeriod(macdSignalPeriod)
        .volumePeriod(volumePeriod)
        .aboveAverageThreshold(genotype.get(11).get(0).doubleValue())
        .lossPercent(genotype.get(12).get(0).doubleValue())
        .profitPercent(genotype.get(13).get(0).doubleValue())
        .adxPeriod(adxPeriod)
        .adxBullishThreshold(genotype.get(15).get(0).intValue())
        .adxBearishThreshold(genotype.get(16).get(0).intValue())
        .volatilityPeriod(volatilityPeriod)
        .contractionThreshold(genotype.get(18).get(0).doubleValue())
        .lowVolatilityThreshold(genotype.get(19).get(0).doubleValue())
        .highVolatilityThreshold(genotype.get(20).get(0).doubleValue())
        .mfiPeriod(mfiPeriod)
        .mfiOverboughtThreshold(genotype.get(22).get(0).intValue())
        .mfiOversoldThreshold(genotype.get(23).get(0).intValue())
        .atrPeriod(atrPeriod)
        .atrThreshold(genotype.get(25).get(0).intValue())
        .lookbackPeriod(lookbackPeriod)
        .supportResistancePeriod(supportResistancePeriod)
        .supportResistanceThreshold(genotype.get(28).get(0).doubleValue())
        .minimumCandles(minimumCandles)
        .build();
  }

  private record OptimizationResult(
      String strategyName,
      StrategyParameters parameters,
      double fitness
  ) {

  }

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