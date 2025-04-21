package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.configuration.VolumeParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
public class VolumeIndicator implements Indicator {

  /**
   * Only if volume is above 20-period average during the signal.
   *
   * @param context context with the list of {@code Bar} objects containing historical data
   * @param params the trading strategy parameters including thresholds and other configuration values
   * @return {@code true} if the buy signal is triggered, {@code false} otherwise
   */
  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    return isVolumeAboveAverage(data, params) && hasIncreasingVolume(data, params.volumePeriod());
  }

  private boolean hasIncreasingVolume(List<Bar> data, int lookbackPeriod) {
    if (data.size() <= lookbackPeriod) {
      return false;
    }

    // Get recent volume values
    List<Double> recentVolumes = new ArrayList<>();
    for (int i = data.size() - lookbackPeriod; i < data.size(); i++) {
      recentVolumes.add(data.get(i).getVolume().doubleValue());
    }

    // Check if volume is trending upward (simple linear regression)
    double volumeSlope = calculateTrendSlope(recentVolumes);

    // Positive slope indicates increasing volume
    return volumeSlope > 0;
  }

  /**
   * Calculates the slope of a linear regression line for the provided data points.
   * A positive slope indicates an upward trend, negative indicates downward trend.
   *
   * @param data List of data points (e.g., volume values)
   * @return The slope of the linear regression line
   */
  private double calculateTrendSlope(List<Double> data) {
    if (data == null || data.size() < 2) {
      return 0.0; // No trend can be calculated with fewer than 2 points
    }

    int n = data.size();

    // Initialize sums
    double sumX = 0.0;
    double sumY = 0.0;
    double sumXY = 0.0;
    double sumX2 = 0.0;

    // Calculate sums needed for the linear regression formula
    for (int i = 0; i < n; i++) {
      double x = i; // Position index
      double y = data.get(i); // Value at that position

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    // Calculate slope using the formula: slope = (n*sumXY - sumX*sumY) / (n*sumX2 - sumX*sumX)
    double denominator = n * sumX2 - sumX * sumX;

    // Avoid division by zero
    if (Math.abs(denominator) < 1e-10) {
      return 0.0;
    }

    // Calculate and return the slope
    return (n * sumXY - sumX * sumY) / denominator;
  }

  /**
   * Ignore if volume is low (weak momentum).
   * @param data current prices
   * @param entryPrice entry price for the asset
   * @param params trade params
   * @return true if the asset should be sold
   */
  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    return isVolumeAboveAverage(data, params);
  }

  private boolean isVolumeAboveAverage(List<Bar> data, VolumeParameters params) {
    int dataSize = data.size();
    List<Bar> volumePeriods = data.subList(Math.max(0, dataSize - params.volumePeriod()), dataSize);
    double avgVolume = calculateAverage(volumePeriods);
    double currentVolume = data.getLast().getVolume().doubleValue();
    log.debug(
        "Average volume: {}, Current volume: {}, Closing time: {}, Above average threshold: {}",
        avgVolume,
        currentVolume, data.getLast().getEndTime(), params.aboveAverageThreshold());
    return currentVolume > avgVolume * (1 + params.aboveAverageThreshold() / 100);
  }

  private double calculateAverage(List<Bar> bars) {
    return bars.stream().mapToDouble(bar -> bar.getVolume().doubleValue()).average().orElse(0.0);
  }
}
