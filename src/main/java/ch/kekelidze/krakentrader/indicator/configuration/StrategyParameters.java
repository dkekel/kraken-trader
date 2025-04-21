package ch.kekelidze.krakentrader.indicator.configuration;

import lombok.Builder;

/**
 * A record that encapsulates the parameters used for a trading strategy. This class is designed to
 * hold configuration values for moving average periods and RSI (Relative Strength Index) thresholds
 * used within a strategy.
 * <p>
 * Attributes: - `movingAverageShortPeriod`: The short period for the moving average (commonly a
 * value between 5 and 15). - `movingAverageLongPeriod`: The long period for the moving average
 * (commonly a value between 20 and 50). - `rsiPeriod`: The period for the RSI calculation
 * (typically a value between 10 and 20). - `rsiBuyThreshold`: The RSI buy threshold value
 * indicating overbought conditions (usually a value between 25 and 35). - `rsiSellThreshold`: The
 * RSI sell threshold value indicating oversold conditions. (e.g., 65-75)
 */
@Builder
public record StrategyParameters(int movingAverageBuyShortPeriod, int movingAverageBuyLongPeriod,
                                 int movingAverageSellShortPeriod, int movingAverageSellLongPeriod,
                                 int rsiPeriod, double rsiBuyThreshold, double rsiSellThreshold,
                                 int macdFastPeriod, int macdSlowPeriod, int macdSignalPeriod,
                                 int volumePeriod, double aboveAverageThreshold,
                                 double lossPercent, double profitPercent,
                                 int adxPeriod, int adxBullishThreshold, int adxBearishThreshold,
                                 int volatilityPeriod, int lookbackPeriod,
                                 int mfiOverboughtThreshold, int mfiOversoldThreshold,
                                 int mfiPeriod, int atrPeriod, int atrThreshold,
                                 int supportResistancePeriod, double supportResistanceThreshold,
                                 int minimumCandles)
    implements MovingAverageParameters, RsiParameters, MacdParameters, VolumeParameters,
    LossProfitParameters, AdxParameters, VolatilityParameters,
    MfiParameters, AtrParameters, SupportResistanceParameters {

}
