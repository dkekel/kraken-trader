package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

  // Cache structure: Map<rsiPeriod, Map<hourKey, List<Double>>>
  // Where List<Double> contains RSI values for different lookbacks, with the most recent at the end
  private final Map<Integer, Map<String, LinkedList<Double>>> rsiCache = new HashMap<>();


  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var symbol = context.getSymbol();
    return hasConsistentRsiImprovement(data, symbol, params);
  }

  private boolean hasConsistentRsiImprovement(List<Bar> data, String coinPair,
      StrategyParameters params) {
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

  private List<Double> calculateLastNRsiValues(List<Bar> data, String coinPair, int lookback,
      int rsiPeriod) {
    // Extract the current hour for cache tracking
    String currentHour = getHourKey(data.getLast().getEndTime());

    // Initialize cache for this period if it doesn't exist
    var periodCache = rsiCache.computeIfAbsent(rsiPeriod, k -> new HashMap<>());
    var cachedValues = periodCache.computeIfAbsent(currentHour, k -> new LinkedList<>());

    // Check if hour has changed by seeing if we have a key that's not the current hour
    // If we do, remove the old hour data as it's no longer needed
    periodCache.keySet().removeIf(hourKey -> !hourKey.equals(currentHour));

    if (cachedValues.isEmpty()) {
      // No cache for this hour yet - calculate all RSI values from scratch
      for (int i = 0; i < lookback; i++) {
        int endIndex = data.size() - i;
        if (endIndex < rsiPeriod) {
          break; // Not enough data to calculate RSI for the period
        }

        List<Bar> subset = data.subList(0, endIndex);
        double rsi = calculateRSI(subset, rsiPeriod);
        cachedValues.addFirst(rsi); // Most recent value will be at the end
      }
    } else {
      // Cache exists - we only need to update the last value
      // Remove the most recent value (it might have changed)
      cachedValues.removeLast();

      // Calculate the most recent RSI value
      double rsi = calculateRSI(data, rsiPeriod);
      cachedValues.addLast(rsi);

      // Ensure we don't exceed the lookback period
      while (cachedValues.size() > lookback) {
        cachedValues.removeFirst();
      }
    }

    return new ArrayList<>(cachedValues);
  }

  /**
   * Create a simple hour key in the format YYYY-MM-DD-HH.
   */
  private String getHourKey(ZonedDateTime time) {
    return String.format("%d-%02d-%02d-%02d",
        time.getYear(), time.getMonthValue(), time.getDayOfMonth(), time.getHour());
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
