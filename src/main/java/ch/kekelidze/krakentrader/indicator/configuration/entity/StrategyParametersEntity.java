package ch.kekelidze.krakentrader.indicator.configuration.entity;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity class for storing strategy parameters in the database. Each record represents a set of
 * parameters for a specific coin pair.
 */
@Entity
@Table(name = "strategy_parameters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StrategyParametersEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "coin_pair", nullable = false, unique = true)
  private String coinPair;

  @Column(name = "strategy_name")
  private String strategyName;

  @Column(name = "moving_average_buy_short_period")
  private int movingAverageBuyShortPeriod;

  @Column(name = "moving_average_buy_long_period")
  private int movingAverageBuyLongPeriod;

  @Column(name = "moving_average_sell_short_period")
  private int movingAverageSellShortPeriod;

  @Column(name = "moving_average_sell_long_period")
  private int movingAverageSellLongPeriod;

  @Column(name = "rsi_period")
  private int rsiPeriod;

  @Column(name = "rsi_buy_threshold")
  private double rsiBuyThreshold;

  @Column(name = "rsi_sell_threshold")
  private double rsiSellThreshold;

  @Column(name = "macd_fast_period")
  private int macdFastPeriod;

  @Column(name = "macd_slow_period")
  private int macdSlowPeriod;

  @Column(name = "macd_signal_period")
  private int macdSignalPeriod;

  @Column(name = "volume_period")
  private int volumePeriod;

  @Column(name = "above_average_threshold")
  private double aboveAverageThreshold;

  @Column(name = "loss_percent")
  private double lossPercent;

  @Column(name = "profit_percent")
  private double profitPercent;

  @Column(name = "adx_period")
  private int adxPeriod;

  @Column(name = "adx_bullish_threshold")
  private int adxBullishThreshold;

  @Column(name = "adx_bearish_threshold")
  private int adxBearishThreshold;

  @Column(name = "volatility_period")
  private int volatilityPeriod;

  @Column(name = "contraction_threshold")
  private double contractionThreshold;

  @Column(name = "low_volatility_threshold")
  private double lowVolatilityThreshold;

  @Column(name = "high_volatility_threshold")
  private double highVolatilityThreshold;

  @Column(name = "mfi_overbought_threshold")
  private int mfiOverboughtThreshold;

  @Column(name = "mfi_oversold_threshold")
  private int mfiOversoldThreshold;

  @Column(name = "mfi_period")
  private int mfiPeriod;

  @Column(name = "atr_period")
  private int atrPeriod;

  @Column(name = "atr_threshold")
  private int atrThreshold;

  @Column(name = "lookback_period")
  private int lookbackPeriod;

  @Column(name = "support_resistance_period")
  private int supportResistancePeriod;

  @Column(name = "support_resistance_threshold")
  private double supportResistanceThreshold;

  @Column(name = "minimum_candles")
  private int minimumCandles;

  /**
   * Converts this entity to a StrategyParameters record.
   *
   * @return a StrategyParameters record with the same values as this entity
   */
  public StrategyParameters toStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(movingAverageBuyShortPeriod)
        .movingAverageBuyLongPeriod(movingAverageBuyLongPeriod)
        .movingAverageSellShortPeriod(movingAverageSellShortPeriod)
        .movingAverageSellLongPeriod(movingAverageSellLongPeriod)
        .rsiPeriod(rsiPeriod)
        .rsiBuyThreshold(rsiBuyThreshold)
        .rsiSellThreshold(rsiSellThreshold)
        .macdFastPeriod(macdFastPeriod)
        .macdSlowPeriod(macdSlowPeriod)
        .macdSignalPeriod(macdSignalPeriod)
        .volumePeriod(volumePeriod)
        .aboveAverageThreshold(aboveAverageThreshold)
        .lossPercent(lossPercent)
        .profitPercent(profitPercent)
        .adxPeriod(adxPeriod)
        .adxBullishThreshold(adxBullishThreshold)
        .adxBearishThreshold(adxBearishThreshold)
        .volatilityPeriod(volatilityPeriod)
        .contractionThreshold(contractionThreshold)
        .lowVolatilityThreshold(lowVolatilityThreshold)
        .highVolatilityThreshold(highVolatilityThreshold)
        .mfiOverboughtThreshold(mfiOverboughtThreshold)
        .mfiOversoldThreshold(mfiOversoldThreshold)
        .mfiPeriod(mfiPeriod)
        .atrPeriod(atrPeriod)
        .atrThreshold(atrThreshold)
        .lookbackPeriod(lookbackPeriod)
        .supportResistancePeriod(supportResistancePeriod)
        .supportResistanceThreshold(supportResistanceThreshold)
        .minimumCandles(minimumCandles)
        .build();
  }

  /**
   * Creates a new entity from a StrategyParameters record, a coin pair, and a strategy name.
   *
   * @param coinPair     the coin pair for which these parameters are valid
   * @param strategyName the name of the strategy to use for this coin pair
   * @param parameters   the strategy parameters
   * @return a new StrategyParametersEntity
   */
  public static StrategyParametersEntity fromStrategyParameters(String coinPair,
      String strategyName, StrategyParameters parameters) {
    return StrategyParametersEntity.builder()
        .coinPair(coinPair)
        .strategyName(strategyName)
        .movingAverageBuyShortPeriod(parameters.movingAverageBuyShortPeriod())
        .movingAverageBuyLongPeriod(parameters.movingAverageBuyLongPeriod())
        .movingAverageSellShortPeriod(parameters.movingAverageSellShortPeriod())
        .movingAverageSellLongPeriod(parameters.movingAverageSellLongPeriod())
        .rsiPeriod(parameters.rsiPeriod())
        .rsiBuyThreshold(parameters.rsiBuyThreshold())
        .rsiSellThreshold(parameters.rsiSellThreshold())
        .macdFastPeriod(parameters.macdFastPeriod())
        .macdSlowPeriod(parameters.macdSlowPeriod())
        .macdSignalPeriod(parameters.macdSignalPeriod())
        .volumePeriod(parameters.volumePeriod())
        .aboveAverageThreshold(parameters.aboveAverageThreshold())
        .lossPercent(parameters.lossPercent())
        .profitPercent(parameters.profitPercent())
        .adxPeriod(parameters.adxPeriod())
        .adxBullishThreshold(parameters.adxBullishThreshold())
        .adxBearishThreshold(parameters.adxBearishThreshold())
        .volatilityPeriod(parameters.volatilityPeriod())
        .contractionThreshold(parameters.contractionThreshold())
        .lowVolatilityThreshold(parameters.lowVolatilityThreshold())
        .highVolatilityThreshold(parameters.highVolatilityThreshold())
        .mfiOverboughtThreshold(parameters.mfiOverboughtThreshold())
        .mfiOversoldThreshold(parameters.mfiOversoldThreshold())
        .mfiPeriod(parameters.mfiPeriod())
        .atrPeriod(parameters.atrPeriod())
        .atrThreshold(parameters.atrThreshold())
        .lookbackPeriod(parameters.lookbackPeriod())
        .supportResistancePeriod(parameters.supportResistancePeriod())
        .supportResistanceThreshold(parameters.supportResistanceThreshold())
        .minimumCandles(parameters.minimumCandles())
        .build();
  }
}
