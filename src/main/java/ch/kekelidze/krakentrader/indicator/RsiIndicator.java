package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
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
public class RsiIndicator implements Indicator {

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    double rsi = calculateRSI(data, params.rsiPeriod());
    log.debug("RSI: {}, Buy threshold: {}, Closing time: {}", rsi, params.rsiBuyThreshold(),
        data.getLast().getEndTime());
    return rsi < params.rsiBuyThreshold();
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double rsi = calculateRSI(data, params.rsiPeriod());
    log.debug("RSI: {}, Sell threshold: {}, Closing time: {}", rsi, params.rsiSellThreshold(),
        data.getLast().getEndTime());
    return rsi > params.rsiSellThreshold();
  }

  /**
   * Calculate RSI values leveraging an existing RSI indicator component
   * Note: This assumes you have an RsiIndicator class that can return values
   */
  public List<Double> calculateRsiValues(List<Bar> data, int rsiPeriod, int lookbackBars) {
    List<Double> rsiValues = new ArrayList<>();
    int size = data.size();

    // Need enough data for RSI calculation (rsiPeriod + lookback)
    if (size < rsiPeriod + 1) {
      return rsiValues;
    }

    // Calculate RSI for each point in our lookback window
    for (int i = 0; i < lookbackBars; i++) {
      // Calculate the end index for the current RSI calculation
      int endIndex = size - lookbackBars + i;

      // Make sure we have enough data
      if (endIndex < rsiPeriod + 1) {
        continue;
      }

      // Create a sublist with enough data to calculate RSI
      // We need at least rsiPeriod+1 bars for proper RSI calculation
      int startIndex = Math.max(0, endIndex - (rsiPeriod + 10)); // Add some extra bars for better accuracy

      // Get the sublist for this specific point's RSI calculation
      List<Bar> rsiData = data.subList(startIndex, endIndex);

      // Use your existing RSI calculator
      double rsiValue = calculateRSI(rsiData, rsiPeriod);
      rsiValues.add(rsiValue);
    }

    return rsiValues;
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
