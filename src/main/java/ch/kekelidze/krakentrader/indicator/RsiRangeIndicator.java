package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
public class RsiRangeIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    return hasConsistentRsiImprovement(data, params);
  }

  private boolean hasConsistentRsiImprovement(List<Bar> data, StrategyParameters params) {
    // Get RSI values for the last few bars
    List<Double> rsiValues = calculateLastNRsiValues(data, params.lookbackPeriod(),
        params.rsiPeriod());

    // Check if RSI is consistently increasing
    boolean isImproving = true;
    for (int i = 1; i < rsiValues.size(); i++) {
      if (rsiValues.get(i) >= rsiValues.get(i-1)) {
        isImproving = false;
        break;
      }
    }

    boolean isRsiWithinThreshold = rsiValues.getLast() >= params.rsiBuyThreshold()
        && rsiValues.getLast() < params.rsiSellThreshold();

    log.debug(
        "RSI improvement: {}, within threshold: {}, RSI values: {}, Buy threshold: {}, "
            + "Sell threshold: {}",
        isImproving, isRsiWithinThreshold, rsiValues, params.rsiBuyThreshold(),
        params.rsiSellThreshold());
    return isImproving && isRsiWithinThreshold;
  }

  private List<Double> calculateLastNRsiValues(List<Bar> data, int lookback, int rsiPeriod) {
    List<Double> rsiValues = new ArrayList<>();

    for (int i = 0; i < lookback; i++) {
      if (data.size() - i < rsiPeriod) {
        break; // Not enough data to calculate RSI for the period
      }

      List<Bar> subset = data.subList(0, data.size() - i);
      double rsi = calculateRSI(subset, rsiPeriod);
      rsiValues.addFirst(rsi); // Add RSI value to the beginning to maintain order
    }

    return rsiValues;
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double rsi = calculateRSI(data, params.rsiPeriod());
    log.debug("RSI: {}, Sell threshold: {}, Closing time: {}", rsi, params.rsiSellThreshold(),
        data.getLast().getEndTime());
    return rsi > params.rsiSellThreshold();
  }

  private double calculateRSI(List<Bar> pricePeriods, int periods) {
    if (pricePeriods.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    RSIIndicator rsi = new RSIIndicator(closePrice, periods);
    return rsi.getValue(series.getEndIndex()).doubleValue();
  }
}
