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
    boolean volumeAboveAverage = isVolumeAboveAverage(data, params);
    boolean volumeIncreasing = hasIncreasingVolume(data, params.volumePeriod());

    // Calculate the recent price action (last 3 bars)
    int size = data.size();
    double recentPriceChange = 0;
    if (size >= 3) {
      double current = data.get(size-1).getClosePrice().doubleValue();
      double previous = data.get(size-3).getClosePrice().doubleValue();
      recentPriceChange = ((current / previous) - 1) * 100;
    }

    // During strong price increases, be more lenient with volume requirements
    boolean isStrongPriceChange = recentPriceChange > 0.5; // 0.5% in recent bars

    // Standard case OR strong price action case
    return (volumeAboveAverage && volumeIncreasing) ||
        (isStrongPriceChange && (volumeAboveAverage || volumeIncreasing));
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
    boolean hasDecreasingVolume = hasDecreasingVolume(data, params.volumePeriod());
    return isVolumeAboveAverage(data, params) || hasDecreasingVolume;
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

  /**
   * Checks if volume is decreasing, which may indicate weakening bullish momentum
   * This is the reverse of hasIncreasingVolume used for buy signals
   *
   * @param data Price bar data
   * @param lookbackPeriod Period to analyze
   * @return true if volume trend is decreasing
   */
  private boolean hasDecreasingVolume(List<Bar> data, int lookbackPeriod) {
    if (data.size() <= lookbackPeriod) {
      return false;
    }

    // Get recent volume values
    List<Double> recentVolumes = new ArrayList<>();
    for (int i = data.size() - lookbackPeriod; i < data.size(); i++) {
      recentVolumes.add(data.get(i).getVolume().doubleValue());
    }

    // Check if volume is trending downward
    double volumeSlope = calculateTrendSlope(recentVolumes);

    // Negative slope indicates decreasing volume
    return volumeSlope < 0;
  }

  private double calculateAverage(List<Bar> bars) {
    return bars.stream().mapToDouble(bar -> bar.getVolume().doubleValue()).average().orElse(0.0);
  }

  /**
   * Detects unusually high volume that often precedes major price movements in crypto
   * @param data Price bar data
   * @param params Strategy parameters
   * @return true if there's a significant volume surge
   */
  public boolean hasVolumeSurge(List<Bar> data, StrategyParameters params) {
    if (data.size() < params.volumePeriod()) return false;

    int size = data.size();
    double currentVolume = data.getLast().getVolume().doubleValue();

    // Calculate average volume excluding the current bar
    double sumVolume = 0;
    for (int i = size - params.volumePeriod(); i < size - 1; i++) {
      sumVolume += data.get(i).getVolume().doubleValue();
    }
    double avgVolume = sumVolume / (params.volumePeriod() - 1);

    // Check if current volume is significantly higher (2x average)
    double volumeRatio = currentVolume / avgVolume;

    // Check price direction on volume surge (more bearish if price drops on high volume)
    boolean isPriceDown = data.getLast().getClosePrice().doubleValue() <
        data.get(size - 2).getClosePrice().doubleValue();

    log.debug("Volume analysis - Current/Avg ratio: {}, Price down: {}", volumeRatio, isPriceDown);

    // Volume surge with price drop is strongly bearish (1.5x volume)
    // Volume surge with price up needs to be more extreme to be considered bearish (2x volume)
    return (isPriceDown && volumeRatio > 1.5) || volumeRatio > 3.0;
  }
}
