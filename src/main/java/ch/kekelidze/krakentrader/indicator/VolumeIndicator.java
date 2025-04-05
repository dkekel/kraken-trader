package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
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
   * @param data the list of {@code Bar} objects containing historical data
   * @param params the trading strategy parameters including thresholds and other configuration values
   * @return {@code true} if the buy signal is triggered, {@code false} otherwise
   */
  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    return isVolumeAboveAverage(data, params);
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

  private boolean isVolumeAboveAverage(List<Bar> data, StrategyParameters params) {
    int dataSize = data.size();
    List<Bar> volumePeriods = data.subList(Math.max(0, dataSize - params.volumePeriod()), dataSize);
    double avgVolume = calculateAverage(volumePeriods);
    double currentVolume = data.getLast().getVolume().doubleValue();
    log.debug("Average volume: {}, Current volume: {}, Above average threshold: {}", avgVolume,
        currentVolume, params.aboveAverageThreshold());
    return currentVolume > avgVolume * (1 + params.aboveAverageThreshold() / 100);
  }

  private double calculateAverage(List<Bar> bars) {
    return bars.stream().mapToDouble(bar -> bar.getVolume().doubleValue()).average().orElse(0.0);
  }
}
