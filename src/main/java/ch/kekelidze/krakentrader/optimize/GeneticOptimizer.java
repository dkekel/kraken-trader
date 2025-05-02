package ch.kekelidze.krakentrader.optimize;

import static io.jenetics.engine.Limits.bySteadyFitness;

import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.service.DataMassageService;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import io.jenetics.EliteSelector;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Mutator;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.SinglePointCrossover;
import io.jenetics.TournamentSelector;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
import io.jenetics.engine.EvolutionStatistics;
import io.jenetics.stat.DoubleMomentStatistics;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component("genericOptimizer")
public class GeneticOptimizer implements Optimizer {

  private static final double initialBalance = 1000;
  private static final int STEADY_FITNESS_GENERATIONS = 10;
  private static final int MAX_GENERATIONS = 20;

  private static List<Bar> historicalData;

  protected static BackTesterService backTesterService;
  protected static DataMassageService dataMassageService;

  public GeneticOptimizer(BackTesterService backTesterService,
      DataMassageService dataMassageService) {
    GeneticOptimizer.backTesterService = backTesterService;
    GeneticOptimizer.dataMassageService = dataMassageService;
  }

  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    historicalData = new ArrayList<>(context.getBars());
    String coinPair = context.getSymbol();

    log.debug("Starting genetic optimization for {}", coinPair);

    Codec<StrategyParameters, IntegerGene> codec = createParameterCodec();

    Engine<IntegerGene, Double> engine = Engine.builder(
            genotype -> fitness(context.getSymbol(), context.getPeriod(), genotype), codec)
        .populationSize(50)
        .optimize(Optimize.MAXIMUM)
        .offspringSelector(new TournamentSelector<>(5))
        .survivorsSelector(new EliteSelector<>())
        .alterers(
            new Mutator<>(0.2), // Add Mutator for genetic diversity
            new SinglePointCrossover<>(0.7) // Include crossover for better exploration
        )
        .build();

    final EvolutionStatistics<Double, DoubleMomentStatistics> statistics =
        EvolutionStatistics.ofNumber();

    Phenotype<IntegerGene, Double> best = engine.stream()
        .limit(bySteadyFitness(STEADY_FITNESS_GENERATIONS))
        .limit(MAX_GENERATIONS)
        .peek(evolutionResult -> {
          statistics.accept(evolutionResult);
          log.debug("Statistics: {}", statistics);
        })
        .collect(EvolutionResult.toBestPhenotype());

    Genotype<IntegerGene> genotype = best.genotype();
    StrategyParameters optimalParams = codec.decode(genotype);
    log.debug("Optimal parameters found: {}", optimalParams);
    log.debug("Best fitness: {}", best.fitness());

    return optimalParams;
  }

  // Default implementation of parameter codec creation - can be overridden by subclasses
  protected Codec<StrategyParameters, IntegerGene> createParameterCodec() {
    // Create chromosome for each parameter with min/max values
    IntegerChromosome maBuyShortChromosome = IntegerChromosome.of(5, 20);
    IntegerChromosome maBuyLongChromosome = IntegerChromosome.of(20, 80);
    IntegerChromosome maSellShortChromosome = IntegerChromosome.of(5, 20);
    IntegerChromosome maSellLongChromosome = IntegerChromosome.of(20, 80);
    IntegerChromosome rsiPeriodChromosome = IntegerChromosome.of(10, 20);
    IntegerChromosome rsiBuyThresholdChromosome = IntegerChromosome.of(25, 35);
    IntegerChromosome rsiSellThresholdChromosome = IntegerChromosome.of(65, 75);
    IntegerChromosome lossPctChromosome = IntegerChromosome.of(3, 10);
    IntegerChromosome profitPctChromosome = IntegerChromosome.of(4, 12);
    IntegerChromosome volatilityThresholdChromosome = IntegerChromosome.of(1, 10);

    // Create genotype with all chromosomes
    return Codec.of(
        Genotype.of(
            maBuyShortChromosome,
            maBuyLongChromosome,
            maSellShortChromosome,
            maSellLongChromosome,
            rsiPeriodChromosome,
            rsiBuyThresholdChromosome,
            rsiSellThresholdChromosome,
            lossPctChromosome,
            profitPctChromosome,
            volatilityThresholdChromosome
        ),
        this::decodeParameters
    );
  }

  protected StrategyParameters decodeParameters(Genotype<IntegerGene> genotype) {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(genotype.get(0).get(0).allele())
        .movingAverageBuyLongPeriod(genotype.get(1).get(0).allele())
        .movingAverageSellShortPeriod(genotype.get(2).get(0).allele())
        .movingAverageSellLongPeriod(genotype.get(3).get(0).allele())
        .rsiPeriod(genotype.get(4).get(0).allele())
        .rsiBuyThreshold(genotype.get(5).get(0).allele())
        .rsiSellThreshold(genotype.get(6).get(0).allele())
        .lossPercent(genotype.get(7).get(0).allele())
        .profitPercent(genotype.get(8).get(0).allele())
        // Default values for other parameters
        .macdFastPeriod(12)
        .macdSlowPeriod(26)
        .macdSignalPeriod(9)
        .volumePeriod(20)
        .aboveAverageThreshold(1.5)
        .adxPeriod(14)
        .adxBullishThreshold(25)
        .adxBearishThreshold(25)
        .volatilityPeriod(20)
        .lookbackPeriod(50)
        .mfiOverboughtThreshold(80)
        .mfiOversoldThreshold(20)
        .mfiPeriod(14)
        .atrPeriod(14)
        .atrThreshold(3)
        .supportResistancePeriod(50)
        .supportResistanceThreshold(0.02)
        .minimumCandles(300)
        .build();
  }

  // Fitness function (Sharpe Ratio)
  private static Double fitness(String coinPair, int period, StrategyParameters params) {
    List<EvaluationContext> regimeContexts = dataMassageService.createMultiRegimeContexts(coinPair,
        period, historicalData);

    // Store results for each regime
    double totalFitness = 0;
    double worstFitness = Double.MAX_VALUE;

    for (EvaluationContext context : regimeContexts) {
      try {
        // Run simulation for this regime
        BacktestResult result = backTesterService.runSimulation(context, params, initialBalance);

        // Calculate fitness for this regime
        double regimeFitness = calculateFitness(result);

        // Track worst-performing regime
        worstFitness = Math.min(worstFitness, regimeFitness);
        totalFitness += regimeFitness;

        log.debug("Regime {} fitness: {}",
            context.getMetadata().getOrDefault("regimeType", "unknown"),
            regimeFitness);
      } catch (Exception e) {
        log.error("Error in fitness evaluation for regime {}: {}",
            context.getSymbol(), e.getMessage());
        return -100.0; // Penalty for failed evaluations
      }
    }

    // Reward parameters that work well across all regimes
    // Weighted average: 60% average performance, 40% worst-case performance
    double averageFitness = totalFitness / regimeContexts.size();
    double combinedFitness = (averageFitness * 0.6) + (worstFitness * 0.4);

    log.debug("Parameters fitness summary - Average: {}, Worst: {}, Combined: {}",
        averageFitness, worstFitness, combinedFitness);

    return combinedFitness;
  }

  private static double calculateFitness(BacktestResult result) {
    // Sharpe ratio is a good base metric
    double fitness = result.sharpeRatio();

    // Penalize strategies with very low win rates
    if (result.winRate() < 0.3) {
      fitness *= 0.5;
    } else {
      // Otherwise reward higher win rates
      fitness *= (1 + result.winRate());
    }

    // Penalize strategies with very few trades
    if (result.totalTrades() < 5) {
      fitness *= 0.5;
    }

    return fitness;
  }

}