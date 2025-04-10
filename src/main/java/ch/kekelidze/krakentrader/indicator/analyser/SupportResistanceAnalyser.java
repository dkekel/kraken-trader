package ch.kekelidze.krakentrader.indicator.analyser;

import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

@Slf4j
@Component
public class SupportResistanceAnalyser {

  /**
   * Identifies resistance levels in a price data series by analyzing past and future price points
   * within a specified lookback period. A resistance level is determined when the current price is
   * the highest within the defined lookback range.
   *
   * @param data     a list of bars containing price data, with each bar representing a time
   *                 interval in the market
   * @param lookback the number of periods to look back and ahead to determine resistance levels
   * @return a list of resistance levels, represented as double values
   */
  public List<Double> findResistanceLevels(List<Bar> data, int lookback) {
    List<Double> resistance = new ArrayList<>();
    var prices = data.stream().map(Bar::getClosePrice).map(Num::doubleValue).toList();
    for (int i = lookback; i < prices.size() - lookback; i++) {
      double current = prices.get(i);
      boolean isHigh = true;
      for (int j = i - lookback; j <= i + lookback; j++) {
        if (prices.get(j) > current) {
          isHigh = false;
          break;
        }
      }
      if (isHigh) {
        resistance.add(current);
      }
    }
    log.debug("Resistance levels: {}", resistance);
    return resistance;
  }

  /**
   * Identifies support levels in a price data series by analyzing past and future price points
   * within a specified lookback period. A support level is determined when the current price is the
   * lowest within the defined lookback range.
   *
   * @param data     a list of bars containing price data, with each bar representing a time
   *                 interval in the market
   * @param lookback the number of periods to look back and ahead to determine support levels
   * @return a list of support levels, represented as double values
   */
  public List<Double> findSupportLevels(List<Bar> data, int lookback) {
    List<Double> support = new ArrayList<>();
    var prices = data.stream().map(Bar::getClosePrice).map(Num::doubleValue).toList();
    for (int i = lookback; i < prices.size() - lookback; i++) {
      double current = prices.get(i);
      boolean isLow = true;
      for (int j = i - lookback; j <= i + lookback; j++) {
        if (prices.get(j) < current) {
          isLow = false;
          break;
        }
      }
      if (isLow) {
        support.add(current);
      }
    }
    log.debug("Support levels: {}", support);
    return support;
  }

  public boolean isNearLevel(double currentPrice, List<Double> levels, double thresholdPercent) {
    var isNearLevel = levels.stream()
        .anyMatch(level -> Math.abs(currentPrice - level) / level <= thresholdPercent / 100);
    log.debug("Current price: {}, Near level: {}", currentPrice, isNearLevel);
    return isNearLevel;
  }
}
