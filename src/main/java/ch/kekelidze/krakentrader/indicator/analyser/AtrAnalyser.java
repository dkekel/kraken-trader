package ch.kekelidze.krakentrader.indicator.analyser;

import java.util.List;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
public class AtrAnalyser {

  /**
   * Calculates the Average True Range (ATR) using an exponential moving average, which is the more
   * standard approach used in trading.
   *
   * @param data   List of price bars
   * @param period The lookback period for ATR calculation (typically 14)
   * @return The ATR value based on exponential moving average
   */
  public double calculateATR(List<Bar> data, int period) {
    // Get the initial ATR using SMA for the first 'period' bars
    double initialSum = 0;
    for (int i = 1; i <= period; i++) {
      Bar current = data.get(i);
      Bar previous = data.get(i - 1);

      double highLowRange =
          current.getHighPrice().doubleValue() - current.getLowPrice().doubleValue();
      double highCloseDiff = Math.abs(
          current.getHighPrice().doubleValue() - previous.getClosePrice().doubleValue());
      double lowCloseDiff = Math.abs(
          current.getLowPrice().doubleValue() - previous.getClosePrice().doubleValue());

      initialSum += Math.max(highLowRange, Math.max(highCloseDiff, lowCloseDiff));
    }

    // Initial ATR value
    double atr = initialSum / period;

    // Apply Wilder's smoothing for the rest of the data
    for (int i = period + 1; i < data.size(); i++) {
      Bar current = data.get(i);
      Bar previous = data.get(i - 1);

      double highLowRange =
          current.getHighPrice().doubleValue() - current.getLowPrice().doubleValue();
      double highCloseDiff = Math.abs(
          current.getHighPrice().doubleValue() - previous.getClosePrice().doubleValue());
      double lowCloseDiff = Math.abs(
          current.getLowPrice().doubleValue() - previous.getClosePrice().doubleValue());

      double trueRange = Math.max(highLowRange, Math.max(highCloseDiff, lowCloseDiff));

      // Wilder's smoothing formula: ATR = ((n-1) * previousATR + currentTR) / n
      atr = ((period - 1) * atr + trueRange) / period;
    }

    return atr;
  }
}
