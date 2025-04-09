package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;

public interface Strategy {

  /**
   * Determines whether a buy signal is present based on the provided price data and trading
   * strategy parameters.
   *
   * @param context context with the coin symbol and a list of price bars, representing the market
   *                data to analyze
   * @param params  the strategy parameters that configure trading rules and thresholds
   * @return true if all the conditions for a buy signal are met; false otherwise
   */
  boolean shouldBuy(EvaluationContext context, StrategyParameters params);

  /**
   * Determines whether a sell action should be executed based on multiple trading indicators.
   *
   * @param context    context with the coin symbol and a list of price bars, representing the
   *                   market data to analyze
   * @param entryPrice the entry price of the trade
   * @param params     the strategy parameters for decision-making
   * @return true if any of the sell signal conditions are met, false otherwise
   */
  boolean shouldSell(EvaluationContext context, double entryPrice, StrategyParameters params);

  default StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(9).movingAverageLongPeriod(21)
        .rsiBuyThreshold(30).rsiSellThreshold(70).rsiPeriod(14)
        .macdFastPeriod(12).macdSlowPeriod(26).macdSignalPeriod(9)
        .adxPeriod(14).adxBullishThreshold(25).adxBearishThreshold(30)
        .mfiPeriod(20).mfiOversoldThreshold(40).mfiOverboughtThreshold(50)
        .lossPercent(5).profitPercent(10)
        .volumePeriod(20)
        .aboveAverageThreshold(20)
        .weightedAgreementThreshold(55)
        .minimumCandles(26)
        .build();
  }

  default int getPeriod() {
    return 60;
  }
}
