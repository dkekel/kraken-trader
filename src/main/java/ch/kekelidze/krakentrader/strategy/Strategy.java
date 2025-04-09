package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import org.ta4j.core.Bar;

public interface Strategy {

  boolean shouldBuy(List<Bar> data, StrategyParameters params);

  boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params);

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
