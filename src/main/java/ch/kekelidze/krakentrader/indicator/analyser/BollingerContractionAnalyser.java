package ch.kekelidze.krakentrader.indicator.analyser;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
public class BollingerContractionAnalyser {

  /**
   * Detects narrowing Bollinger Bands, which often indicate consolidation/sideways markets.
   *
   * @param data List of price bars
   * @param period Bollinger Band period (typically 20)
   * @param contractionThreshold Minimum bandwidth to consider as contraction
   * @return true if Bollinger Bands are contracting/narrowed
   */
  public boolean hasBollingerContraction(List<Bar> data, int period, double contractionThreshold) {
    if (data.size() < period + 10) { // Need extra for bandwidth trend
      return false;
    }

    // Calculate Bollinger Bands and bandwidth
    List<Double> bandwidths = new ArrayList<>();

    for (int i = period - 1; i < data.size(); i++) {
      // Calculate the simple moving average
      double sum = 0;
      for (int j = 0; j < period; j++) {
        sum += data.get(i - j).getClosePrice().doubleValue();
      }
      double sma = sum / period;

      // Calculate standard deviation
      double squaredSum = 0;
      for (int j = 0; j < period; j++) {
        double deviation = data.get(i - j).getClosePrice().doubleValue() - sma;
        squaredSum += deviation * deviation;
      }
      double stdDev = Math.sqrt(squaredSum / period);

      // Calculate Bollinger Bands
      double upperBand = sma + (stdDev * 2);
      double lowerBand = sma - (stdDev * 2);

      // Calculate bandwidth as percentage of middle band
      double bandwidth = ((upperBand - lowerBand) / sma) * 100;
      bandwidths.add(bandwidth);
    }

    // Get the recent bandwidth values
    List<Double> recentBandwidths = bandwidths.subList(bandwidths.size() - 10, bandwidths.size());

    // Check if current bandwidth is below threshold
    double currentBandwidth = recentBandwidths.getLast();

    return currentBandwidth < contractionThreshold;
  }
}
