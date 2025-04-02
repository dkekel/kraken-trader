package ch.kekelidze.krakentrader.indicator.service.strategy;

import static io.jenetics.engine.Limits.bySteadyFitness;

import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.service.strategy.configuration.StrategyParameters;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.Mutator;
import io.jenetics.Optimize;
import io.jenetics.Phenotype;
import io.jenetics.SinglePointCrossover;
import io.jenetics.engine.Codec;
import io.jenetics.engine.Engine;
import io.jenetics.engine.EvolutionResult;
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
  public StrategyParameters optimizeParameters(List<Bar> data) {
    historicalData = new ArrayList<>(data);

    Engine<IntegerGene, Double> engine = Engine.builder(
            GeneticOptimizer::fitness,
            Codec.of(
                Genotype.of(
                    IntegerChromosome.of(5, 15),    // movingAverageShortPeriod
                    IntegerChromosome.of(20, 50),   // movingAverageLongPeriod
                    IntegerChromosome.of(10, 20),   // rsiPeriod
                    IntegerChromosome.of(25, 35)    // rsiBuyThreshold
                ),
                gt -> gt
            ))
        .populationSize(50) // Increase population size
        .optimize(Optimize.MAXIMUM)
        .alterers(
            new Mutator<>(0.3), // Add Mutator for genetic diversity
            new SinglePointCrossover<>(0.5) // Include crossover for better exploration
        )
        .build();

    Phenotype<IntegerGene, Double> best = engine.stream()
        .limit(bySteadyFitness(100)) // Terminate when fitness stabilizes over generations
        .collect(EvolutionResult.toBestPhenotype());

    Genotype<IntegerGene> genotype = best.genotype();
    log.info("Best fitness: {}", best.fitness());
    return new StrategyParameters(
        genotype.get(0).get(0).intValue(),  // maShort
        genotype.get(1).get(0).intValue(),  // maLong
        genotype.get(2).get(0).intValue(),  // rsiPeriod
        genotype.get(3).get(0).intValue(),  // rsiBuy
        70                                  // rsiSell (fixed)
    );
  }

  // Fitness function (Sharpe Ratio)
  private static Double fitness(Genotype<IntegerGene> genotype) {
    StrategyParameters params = new StrategyParameters(
        genotype.get(0).get(0).intValue(),
        genotype.get(1).get(0).intValue(),
        genotype.get(2).get(0).intValue(),
        genotype.get(3).get(0).intValue(),
        70
    );
    BacktestResult result = backTesterService.runSimulation(historicalData, params, initialBalance);
    // Example combined fitness function
    return result.sharpeRatio() * 0.6 +
        (1 - result.maxDrawdown()) * 0.3 +
        result.winRate() * 0.1;
  }
}