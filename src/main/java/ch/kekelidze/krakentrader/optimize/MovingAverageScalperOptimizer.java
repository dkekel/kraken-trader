package ch.kekelidze.krakentrader.optimize;

import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.engine.Codec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * A specialized genetic optimizer for the MovingAverageScalper strategy. This optimizer focuses on
 * parameters specifically relevant to the MovingAverageScalper strategy, allowing for coin-specific
 * parameter optimization.
 */
@Slf4j
@Component("movingAverageScalperOptimizer")
public class MovingAverageScalperOptimizer extends GeneticOptimizer {

  public MovingAverageScalperOptimizer(BackTesterService backTesterService) {
    super(backTesterService);
  }

  /**
   * Creates specialized parameter ranges for MovingAverageScalper strategy based on the coin pair.
   * Different coins may benefit from different parameter settings.
   *
   * @param coinPair The cryptocurrency pair being optimized (e.g., "XDGUSD", "ETHUSD")
   * @return A codec that maps genotypes to strategy parameters with coin-specific ranges
   */
  protected Codec<StrategyParameters, IntegerGene> createParameterCodec(String coinPair) {
    // Define min/max ranges based on coin
    int[] maBuyShortRange = getMovingAverageBuyShortRange(coinPair);
    int[] maBuyLongRange = getMovingAverageBuyLongRange(coinPair);
    int[] maSellShortRange = getMovingAverageSellShortRange(coinPair);
    int[] maSellLongRange = getMovingAverageSellLongRange(coinPair);
    int[] rsiPeriodRange = getRsiPeriodRange(coinPair);
    double[] rsiBuyThresholdRange = getRsiBuyThresholdRange(coinPair);
    double[] rsiSellThresholdRange = getRsiSellThresholdRange(coinPair);
    double[] lossPctRange = getLossPercentRange(coinPair);
    double[] profitPctRange = getProfitPercentRange(coinPair);

    // Create chromosome for MA parameters
    IntegerChromosome maBuyShortChromosome = IntegerChromosome.of(maBuyShortRange[0],
        maBuyShortRange[1]);
    IntegerChromosome maBuyLongChromosome = IntegerChromosome.of(maBuyLongRange[0],
        maBuyLongRange[1]);
    IntegerChromosome maSellShortChromosome = IntegerChromosome.of(maSellShortRange[0],
        maSellShortRange[1]);
    IntegerChromosome maSellLongChromosome = IntegerChromosome.of(maSellLongRange[0],
        maSellLongRange[1]);

    // Create chromosome for RSI parameters
    IntegerChromosome rsiPeriodChromosome = IntegerChromosome.of(rsiPeriodRange[0],
        rsiPeriodRange[1]);
    IntegerChromosome rsiBuyThresholdChromosome = IntegerChromosome.of(
        (int) (rsiBuyThresholdRange[0]), (int) (rsiBuyThresholdRange[1]));
    IntegerChromosome rsiSellThresholdChromosome = IntegerChromosome.of(
        (int) (rsiSellThresholdRange[0]), (int) (rsiSellThresholdRange[1]));

    // Create chromosome for risk management parameters
    IntegerChromosome lossPctChromosome = IntegerChromosome.of(
        (int) (lossPctRange[0]), (int) (lossPctRange[1]));
    IntegerChromosome profitPctChromosome = IntegerChromosome.of(
        (int) (profitPctRange[0]), (int) (profitPctRange[1]));

    IntegerChromosome volatilityThresholdChromosome = IntegerChromosome.of(1, 10);

    // Create the genotype with all chromosomes
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
        // Map the genotype to strategy parameters
        gt -> {
          StrategyParameters.StrategyParametersBuilder builder = StrategyParameters.builder();

          builder.movingAverageBuyShortPeriod(gt.get(0).get(0).allele())
              .movingAverageBuyLongPeriod(gt.get(1).get(0).allele())
              .movingAverageSellShortPeriod(gt.get(2).get(0).allele())
              .movingAverageSellLongPeriod(gt.get(3).get(0).allele())
              .rsiPeriod(gt.get(4).get(0).allele())
              .rsiBuyThreshold(gt.get(5).get(0).allele())
              .rsiSellThreshold(gt.get(6).get(0).allele())
              .lossPercent(gt.get(7).get(0).allele())
              .profitPercent(gt.get(8).get(0).allele())
              .highVolatilityThreshold(gt.get(9).get(0).allele());

          // Set reasonable defaults for other parameters not being optimized
          setDefaultParameters(builder);

          return builder.build();
        }
    );
  }

  /**
   * Set default values for parameters that aren't being optimized
   */
  private void setDefaultParameters(StrategyParameters.StrategyParametersBuilder builder) {
    builder.macdFastPeriod(12)
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
        .minimumCandles(300);
  }

  /**
   * Optimizes strategy parameters based on historical data for the specific coin pair
   */
  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    log.debug("Starting MovingAverageScalper optimization for {}", context.getSymbol());
    return super.optimizeParameters(context);
  }

  // Parameter range methods with coin-specific customizations

  private int[] getMovingAverageBuyShortRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new int[]{3, 10}; // Higher performing coin - tighter range
      case "ETHUSD":
        return new int[]{3, 10}; // Higher performing coin - tighter range
      case "LTCUSD":
        return new int[]{5, 15}; // Medium performer
      default:
        return new int[]{5, 20}; // Default wider range for other coins
    }
  }

  private int[] getMovingAverageBuyLongRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new int[]{15, 50};
      case "ETHUSD":
        return new int[]{20, 60};
      case "LTCUSD":
        return new int[]{25, 70};
      default:
        return new int[]{20, 80};
    }
  }

  private int[] getMovingAverageSellShortRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new int[]{3, 10};
      case "ETHUSD":
        return new int[]{3, 10};
      case "LTCUSD":
        return new int[]{5, 15};
      default:
        return new int[]{5, 20};
    }
  }

  private int[] getMovingAverageSellLongRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new int[]{15, 50};
      case "ETHUSD":
        return new int[]{20, 60};
      case "LTCUSD":
        return new int[]{25, 70};
      default:
        return new int[]{20, 80};
    }
  }

  private int[] getRsiPeriodRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new int[]{7, 14};
      case "ETHUSD":
        return new int[]{7, 14};
      default:
        return new int[]{10, 20};
    }
  }

  private double[] getRsiBuyThresholdRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new double[]{20, 40}; // More aggressive
      case "ETHUSD":
        return new double[]{20, 40}; // More aggressive
      default:
        return new double[]{25, 35}; // More conservative
    }
  }

  private double[] getRsiSellThresholdRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new double[]{60, 80}; // More aggressive
      case "ETHUSD":
        return new double[]{60, 80}; // More aggressive
      default:
        return new double[]{65, 75}; // More conservative
    }
  }

  private double[] getLossPercentRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new double[]{2, 5}; // Lower risk for high performer
      case "ETHUSD":
        return new double[]{1, 3}; // Lower risk for high performer
      case "XRPUSD":
        return new double[]{5, 15}; // Higher loss tolerance for poor performer
      default:
        return new double[]{3, 10}; // Default range
    }
  }

  private double[] getProfitPercentRange(String coinPair) {
    switch (coinPair) {
      case "XDGUSD":
        return new double[]{3, 8}; // Higher profit target
      case "ETHUSD":
        return new double[]{5, 15}; // Higher profit target
      case "XRPUSD":
        return new double[]{6, 20}; // Much higher profit target needed
      default:
        return new double[]{4, 12}; // Default range
    }
  }
}