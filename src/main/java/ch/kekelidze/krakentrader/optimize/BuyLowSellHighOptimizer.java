package ch.kekelidze.krakentrader.optimize;

import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.service.DataMassageService;
import io.jenetics.Genotype;
import io.jenetics.IntegerChromosome;
import io.jenetics.IntegerGene;
import io.jenetics.engine.Codec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("buyLowSellHighOptimizer")
public class BuyLowSellHighOptimizer extends GeneticOptimizer {

  public BuyLowSellHighOptimizer(BackTesterService backTesterService,
      DataMassageService dataMassageService) {
    super(backTesterService, dataMassageService);
  }

  protected Codec<StrategyParameters, IntegerGene> createParameterCodec() {
    // Define the chromosomes for each parameter and their ranges
    // Only including parameters that are actually used in the strategy
    return Codec.of(
        Genotype.of(
            // Moving Average parameters
            IntegerChromosome.of(10, 30),      // MA buy short period (10-30)
            IntegerChromosome.of(40, 100),     // MA buy long period (40-100)

            // RSI parameters
            IntegerChromosome.of(10, 18),      // RSI period (10-18)
            IntegerChromosome.of(30, 50),      // RSI buy threshold (30-50)
            IntegerChromosome.of(65, 80),      // RSI sell threshold (65-80)

            // Trend/Lookback parameters
            IntegerChromosome.of(3, 8),        // Lookback period (3-8)

            // MACD parameters
            IntegerChromosome.of(8, 16),       // MACD fast period (8-16)
            IntegerChromosome.of(20, 32),      // MACD slow period (20-32)
            IntegerChromosome.of(7, 12),       // MACD signal period (7-12)

            // Volatility parameters
            IntegerChromosome.of(10, 20),      // ATR period (10-20)
            IntegerChromosome.of(70, 110),     // Low volatility threshold * 100 (0.7-1.1)
            IntegerChromosome.of(120, 180),    // High volatility threshold * 100 (1.2-1.8)

            // Volume parameters
            IntegerChromosome.of(12, 36),      // Volume period (12-36)
            IntegerChromosome.of(10, 40),      // Above average threshold (10-40%)

            // Risk parameters
            IntegerChromosome.of(15, 50),      // Loss percent * 10 (1.5-5.0%)
            IntegerChromosome.of(80, 200),     // Profit percent * 10 (8-20%)

            // Contraction parameter
            IntegerChromosome.of(20, 50)       // Contraction threshold * 10 (2.0-5.0)

            // Minimum candles is now calculated dynamically based on other periods
        ),
        this::decodeParameters
    );
  }

  protected StrategyParameters decodeParameters(Genotype<IntegerGene> genotype) {
    int movingAverageBuyShortPeriod = genotype.get(0).get(0).intValue();
    int movingAverageBuyLongPeriod = genotype.get(1).get(0).intValue();

    int rsiPeriod = genotype.get(2).get(0).intValue();
    int rsiBuyThreshold = genotype.get(3).get(0).intValue();
    int rsiSellThreshold = genotype.get(4).get(0).intValue();

    int lookbackPeriod = genotype.get(5).get(0).intValue();

    int macdFastPeriod = genotype.get(6).get(0).intValue();
    int macdSlowPeriod = genotype.get(7).get(0).intValue();
    int macdSignalPeriod = genotype.get(8).get(0).intValue();

    int atrPeriod = genotype.get(9).get(0).intValue();
    double lowVolatilityThreshold = genotype.get(10).get(0).intValue() / 100.0;
    double highVolatilityThreshold = genotype.get(11).get(0).intValue() / 100.0;

    int volumePeriod = genotype.get(12).get(0).intValue();
    int aboveAverageThreshold = genotype.get(13).get(0).intValue();

    double lossPercent = genotype.get(14).get(0).intValue() / 10.0;
    double profitPercent = genotype.get(15).get(0).intValue() / 10.0;

    double contractionThreshold = genotype.get(16).get(0).intValue() / 10.0;

    // 5. Calculate minimum candles as 3x the maximum period
    int maxPeriod = Math.max(
        Math.max(movingAverageBuyLongPeriod, volumePeriod),
        Math.max(Math.max(rsiPeriod, atrPeriod),
            Math.max(macdSlowPeriod + macdSignalPeriod,
                lookbackPeriod))
    );
    int minimumCandles = maxPeriod * 3;

    // Create and return the strategy parameters with exactly the parameters from getStrategyParameters
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(movingAverageBuyShortPeriod)
        .movingAverageBuyLongPeriod(movingAverageBuyLongPeriod)
        .rsiBuyThreshold(rsiBuyThreshold)
        .rsiSellThreshold(rsiSellThreshold)
        .rsiPeriod(rsiPeriod)
        .lookbackPeriod(lookbackPeriod)
        .macdFastPeriod(macdFastPeriod)
        .macdSlowPeriod(macdSlowPeriod)
        .macdSignalPeriod(macdSignalPeriod)
        .atrPeriod(atrPeriod)
        .lowVolatilityThreshold(lowVolatilityThreshold)
        .highVolatilityThreshold(highVolatilityThreshold)
        .volumePeriod(volumePeriod)
        .aboveAverageThreshold(aboveAverageThreshold)
        .lossPercent(lossPercent)
        .profitPercent(profitPercent)
        .contractionThreshold(contractionThreshold)
        .minimumCandles(minimumCandles)  // Dynamically calculated based on other periods
        .build();
  }
}