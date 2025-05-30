package ch.kekelidze.krakentrader.backtester.util;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;

public class TimeFrameAdjustmentUtils {

  /**
   * Adjusts the time frame of the given StrategyParameters by recalculating the periods based on
   * the provided period value. The calculation is performed using a time frame multiplier derived
   * from the ratio of 60 (1h candles) to the given period.
   *
   * @param parameters the existing StrategyParameters object containing configuration values to be
   *                   adjusted
   * @param period     the new period used to adjust the time frame; it must be a divisor of 60
   * @return a new StrategyParameters object with adjusted period values, while retaining
   * non-period-related attributes unchanged
   */
  public static StrategyParameters adjustTimeFrame(StrategyParameters parameters, int period) {
    var timeFrameMultiplier = 60 / period;
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(parameters.movingAverageBuyShortPeriod() * timeFrameMultiplier)
        .movingAverageBuyLongPeriod(parameters.movingAverageBuyLongPeriod() * timeFrameMultiplier)
        .movingAverageSellShortPeriod(
            parameters.movingAverageSellShortPeriod() * timeFrameMultiplier)
        .movingAverageSellLongPeriod(parameters.movingAverageSellLongPeriod() * timeFrameMultiplier)
        .macdSlowPeriod(parameters.macdSlowPeriod() * timeFrameMultiplier)
        .macdFastPeriod(parameters.macdFastPeriod() * timeFrameMultiplier)
        .macdSignalPeriod(parameters.macdSignalPeriod() * timeFrameMultiplier)
        .atrPeriod(parameters.atrPeriod() * timeFrameMultiplier)
        .atrThreshold(parameters.atrThreshold())
        .rsiPeriod(parameters.rsiPeriod() * timeFrameMultiplier)
        .rsiBuyThreshold(parameters.rsiBuyThreshold())
        .rsiSellThreshold(parameters.rsiSellThreshold())
        .lookbackPeriod(parameters.lookbackPeriod() * timeFrameMultiplier)
        .volumePeriod(parameters.volumePeriod() * timeFrameMultiplier)
        .aboveAverageThreshold(parameters.aboveAverageThreshold())
        .volumeSurgeBearishThreshold(parameters.volumeSurgeBearishThreshold())
        .volumeSurgeExtremeBearishThreshold(parameters.volumeSurgeExtremeBearishThreshold())
        .bearishPatternLookbackPeriod(
            parameters.bearishPatternLookbackPeriod() * timeFrameMultiplier)
        .volatilityPeriod(parameters.volatilityPeriod() * timeFrameMultiplier)
        .lowVolatilityThreshold(parameters.lowVolatilityThreshold())
        .highVolatilityThreshold(parameters.highVolatilityThreshold())
        .lossPercent(parameters.lossPercent())
        .profitPercent(parameters.profitPercent())
        .contractionThreshold(parameters.contractionThreshold())
        .supportResistanceThreshold(parameters.supportResistanceThreshold())
        .supportResistancePeriod(parameters.supportResistancePeriod() * timeFrameMultiplier)
        .minimumCandles(parameters.minimumCandles() * timeFrameMultiplier)
        .build();

  }
}
