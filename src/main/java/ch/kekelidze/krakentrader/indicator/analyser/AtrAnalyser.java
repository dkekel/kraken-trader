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
    int endIndex = data.size() - 1;
    int startIndex = Math.max(0, endIndex - period);

    if (endIndex - startIndex < period - 1) {
      return 0; // Not enough data
    }

    double sumTrueRange = 0;

    for (int i = startIndex + 1; i <= endIndex; i++) {
      Bar current = data.get(i);
      Bar previous = data.get(i - 1);

      double highLowRange =
          current.getHighPrice().doubleValue() - current.getLowPrice().doubleValue();
      double highCloseDiff = Math.abs(
          current.getHighPrice().doubleValue() - previous.getClosePrice().doubleValue());
      double lowCloseDiff = Math.abs(
          current.getLowPrice().doubleValue() - previous.getClosePrice().doubleValue());

      double trueRange = Math.max(highLowRange, Math.max(highCloseDiff, lowCloseDiff));
      sumTrueRange += trueRange;
    }

    return sumTrueRange / period;
  }
}
