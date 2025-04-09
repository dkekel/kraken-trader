package ch.kekelidze.krakentrader.indicator.optimize;

import static io.jenetics.engine.Limits.bySteadyFitness;

import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
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
@Component
public class GeneticOptimizer implements Optimizer {

  private static List<Bar> historicalData;
  private static final double initialBalance = 10000;

  private static BackTesterService backTesterService;

  public GeneticOptimizer(BackTesterService backTesterService) {
    GeneticOptimizer.backTesterService = backTesterService;
  }

  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    historicalData = new ArrayList<>(context.getBars());

    Engine<IntegerGene, Double> engine = Engine.builder(
            genotype -> fitness(context.getSymbol(), genotype),
            Codec.of(
                Genotype.of(
                    IntegerChromosome.of(5, 15),    // movingAverageShortPeriod
                    IntegerChromosome.of(20, 30),   // movingAverageLongPeriod
                    IntegerChromosome.of(10, 20),   // rsiPeriod
                    IntegerChromosome.of(25, 35),   // rsiBuyThreshold
                    IntegerChromosome.of(10, 20),   // macdFastPeriod MA divergence
                    IntegerChromosome.of(20, 30),   // macdSlowPeriod MA divergence
                    IntegerChromosome.of(15, 25),   // adx bullish threshold
                    IntegerChromosome.of(30, 35),   // adx bearish threshold
                    IntegerChromosome.of(10, 20)   // average volume threshold
//                    IntegerChromosome.of(50, 80)    // weighted agreement threshold
                ),
                gt -> gt
            ))
        .populationSize(50) // Increase population size
        .optimize(Optimize.MAXIMUM)
//        .alterers(
//            new Mutator<>(0.3), // Add Mutator for genetic diversity
//            new SinglePointCrossover<>(0.5) // Include crossover for better exploration
//        )
        .build();
    final EvolutionStatistics<Double, DoubleMomentStatistics> statistics =
        EvolutionStatistics.ofNumber();
    Phenotype<IntegerGene, Double> best = engine.stream()
        .limit(bySteadyFitness(100))
        .peek(evolutionResult -> {
          statistics.accept(evolutionResult);
          log.info("Statistics: {}", statistics);
        })
        .collect(EvolutionResult.toBestPhenotype());

    Genotype<IntegerGene> genotype = best.genotype();
    log.info("Best fitness: {}", best.fitness());
    return getStrategyParameters(genotype);
  }

  // Fitness function (Sharpe Ratio)
  private static Double fitness(String coinPair, Genotype<IntegerGene> genotype) {
    StrategyParameters params = getStrategyParameters(genotype);
    var evaluationContext = EvaluationContext.builder().symbol(coinPair).bars(historicalData)
        .build();
    BacktestResult result = backTesterService.runSimulation(evaluationContext, params,
        initialBalance);
    return result.sharpeRatio();
    // Example combined fitness function
//    return result.sharpeRatio() * 0.6 +
//        (1 - result.maxDrawdown()) * 0.3 +
//        result.winRate() * 0.1;
  }

  private static StrategyParameters getStrategyParameters(Genotype<IntegerGene> genotype) {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(genotype.get(0).get(0).intValue())
        .movingAverageLongPeriod(genotype.get(1).get(0).intValue())
        .rsiPeriod(genotype.get(2).get(0).intValue())
        .rsiBuyThreshold(genotype.get(3).get(0).intValue())
        .macdFastPeriod(genotype.get(4).get(0).intValue())
        .macdSlowPeriod(genotype.get(5).get(0).intValue())
        .adxBullishThreshold(genotype.get(6).get(0).intValue())
        .adxBearishThreshold(genotype.get(7).get(0).intValue())
        .aboveAverageThreshold(genotype.get(8).get(0).intValue())
//        .weightedAgreementThreshold(genotype.get(9).get(0).intValue())
        .rsiSellThreshold(70)
        .lossPercent(5)
        .profitPercent(10)
        .macdSignalPeriod(9)
        .adxPeriod(14)
        .volumePeriod(20)
        .build();
  }
}