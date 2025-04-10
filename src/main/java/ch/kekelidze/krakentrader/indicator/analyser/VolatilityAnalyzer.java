package ch.kekelidze.krakentrader.indicator.analyser;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;

@Slf4j
@Component
public class VolatilityAnalyzer {

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
    double currentATR = calculateATR(series, atrPeriod);
    double sum = 0;
    for (int i = series.getEndIndex() - lookback; i < series.getEndIndex(); i++) {
      sum += new ATRIndicator(series, atrPeriod).getValue(i).doubleValue();
    }
    double avgATR = sum / lookback;
    log.debug("ATR: {}, Average ATR: {}", currentATR, avgATR);
    return currentATR < avgATR;
  }

  private double calculateATR(BarSeries series, int period) {
    ATRIndicator atr = new ATRIndicator(series, period);
    return atr.getValue(series.getEndIndex()).doubleValue();
  }
}
