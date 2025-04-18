package ch.kekelidze.krakentrader.indicator.analyser;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatilityAnalyser {

  private final AtrAnalyser atrAnalyser;

  public boolean isVolatilityAcceptable(List<Bar> data, StrategyParameters params) {
    double atrPercent = calculateATRPercent(data, params.atrPeriod());
    return atrPercent <= params.atrThreshold();
  }

  // Calculate ATR as a percentage of price
  private double calculateATRPercent(List<Bar> data, int period) {
    double atr = atrAnalyser.calculateATR(data, period);
    double currentPrice = data.getLast().getClosePrice().doubleValue();

    return (atr / currentPrice) * 100;
  }

  /**
   * Determines if the volatility, as measured by the Average True Range (ATR), is decreasing. It
   * compares the current ATR value with the average ATR value computed over a specified lookback
   * period.
   *
   * @param data    the bar series containing OHLC (Open, High, Low, Close) data
   * @param atrPeriod the period over which the ATR is calculated
   * @param lookback  the number of previous periods to use for computing the average ATR
   * @return true if the current ATR is less than the average ATR over the lookback period;
   * otherwise, false
   */
  public boolean isVolatilityDecreasing(List<Bar> data, int atrPeriod, int lookback) {
    var series = new BaseBarSeriesBuilder().withBars(data).build();
    double currentATR = atrAnalyser.calculateATR(data, atrPeriod);
    double sum = 0;
    for (int i = series.getEndIndex() - lookback; i < series.getEndIndex(); i++) {
      sum += new ATRIndicator(series, atrPeriod).getValue(i).doubleValue();
    }
    double avgATR = sum / lookback;
    log.debug("ATR: {}, Average ATR: {}", currentATR, avgATR);
    return currentATR < avgATR;
  }
}
