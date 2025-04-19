package ch.kekelidze.krakentrader.indicator.analyser;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.configuration.VolatilityParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.ATRIndicator;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatilityAnalyser {

  private final AtrAnalyser atrAnalyser;

  public boolean isVolatilityAcceptable(List<Bar> data, StrategyParameters params) {
    double atrPercent = calculateATRPercent(data, params.atrPeriod());
    return atrPercent <= params.atrThreshold();
  }

  // Calculate ATR as a percentage of price
  private double calculateATRPercent(List<Bar> data, int period) {
    double atr = atrAnalyser.calculateATR(data, period);
    double currentPrice = data.getLast().getClosePrice().doubleValue();

    return (atr / currentPrice) * 100;
  }

  /**
   * Determines if the volatility, as measured by the Average True Range (ATR), is decreasing. It
   * compares the current ATR value with the average ATR value computed over a specified lookback
   * period.
   *
   * @param data    the bar series containing OHLC (Open, High, Low, Close) data
   * @param atrPeriod the period over which the ATR is calculated
   * @param lookback  the number of previous periods to use for computing the average ATR
   * @return true if the current ATR is less than the average ATR over the lookback period;
   * otherwise, false
   */
  public boolean isVolatilityDecreasing(List<Bar> data, int atrPeriod, int lookback) {
    var series = new BaseBarSeriesBuilder().withBars(data).build();
    double currentATR = atrAnalyser.calculateATR(data, atrPeriod);
    double sum = 0;
    for (int i = series.getEndIndex() - lookback; i < series.getEndIndex(); i++) {
      sum += new ATRIndicator(series, atrPeriod).getValue(i).doubleValue();
    }
    double avgATR = sum / lookback;
    log.debug("ATR: {}, Average ATR: {}", currentATR, avgATR);
    return currentATR < avgATR;
  }

  /**
   * Determines if the market is currently experiencing low volatility,
   * which can indicate a ranging market suitable for counter-trend strategies.
   *
   * @param context The evaluation context containing price data
   * @return true if volatility is considered low, false otherwise
   */
  public boolean isLowVolatility(EvaluationContext context,
      VolatilityParameters volatilityParameters) {
    // Get price bars from context
    var priceBars = context.getBars();
    var barsToAnalyze = Math.min(72, priceBars.size());
    var subList = priceBars.subList(priceBars.size() - barsToAnalyze, priceBars.size());

    // Calculate Average True Range (ATR) for recent period (e.g., 14 bars)
    double recentATR = atrAnalyser.calculateATR(subList, volatilityParameters.volatilityPeriod());

    // Calculate ATR for a longer historical period (e.g., 30 bars)
    double historicalATR = atrAnalyser.calculateATR(subList, 30);

    // Compare recent ATR to historical ATR
    // If recent ATR is significantly lower (e.g., 30% lower), we consider it low volatility
    return recentATR < (historicalATR * 0.65);
  }

  /**
   * Determines if the market is trading within a defined range,
   * which is suitable for counter-trend strategies.
   *
   * @param context The evaluation context containing price data
   * @return true if the market is in a defined range, false otherwise
   */
  public boolean isInDefinedRange(EvaluationContext context) {
    // Get price bars from context
    var priceBars = context.getBars();
    var barsToAnalyze = Math.min(72, priceBars.size());
    var subList = priceBars.subList(priceBars.size() - barsToAnalyze, priceBars.size());

    // Find highest high and lowest low in the period
    double highestHigh = Double.MIN_VALUE;
    double lowestLow = Double.MAX_VALUE;

    for (int i = 0; i < barsToAnalyze; i++) {
      Bar bar = subList.get(i);
      double high = bar.getHighPrice().doubleValue();
      double low = bar.getLowPrice().doubleValue();
      if (high > highestHigh) {
        highestHigh = high;
      }
      if (low < lowestLow) {
        lowestLow = low;
      }
    }

    // Calculate range as a percentage
    double rangePercent = ((highestHigh - lowestLow) / lowestLow) * 100.0;

    // Check if price has tested the range boundaries
    boolean testedUpper = false;
    boolean testedLower = false;

    // Define what constitutes "testing" a boundary (e.g., within 1% of range boundaries)
    double testThreshold = 0.02 * (highestHigh - lowestLow);

    for (int i = 0; i < barsToAnalyze; i++) {
      Bar bar = subList.get(i);
      double high = bar.getHighPrice().doubleValue();
      double low = bar.getLowPrice().doubleValue();
      // Check if price tested upper boundary
      if (highestHigh - high < testThreshold) {
        testedUpper = true;
      }

      // Check if price tested lower boundary
      if (low - lowestLow < testThreshold) {
        testedLower = true;
      }
    }

    // For a defined range, we require:
    // 1. Range is relatively narrow (e.g., less than 10% for crypto)
    // 2. Price has tested both upper and lower boundaries
    boolean narrowRange = rangePercent < 7.5;

    // Check for directional bias (trend)
    boolean hasDirectionalBias = hasSignificantTrend(subList, barsToAnalyze);

    // Consider it a range if it's narrow, has tested boundaries, and doesn't have strong directional bias
    return narrowRange && testedUpper && testedLower && !hasDirectionalBias;
  }

  /**
   * Determines if there is a significant trend in the price data
   *
   * @param priceBars The list of price bars
   * @param barsToAnalyze Number of bars to analyze
   * @return true if a significant trend is detected, false otherwise
   */
  private boolean hasSignificantTrend(List<Bar> priceBars, int barsToAnalyze) {
    // Simple linear regression to detect trend
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < barsToAnalyze; i++) {
      double x = i;
      double y = priceBars.get(i).getClosePrice().doubleValue();

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    // Calculate slope of the linear regression line
    double slope = ((double) barsToAnalyze * sumXY - sumX * sumY) 
        / ((double) barsToAnalyze * sumX2 - sumX * sumX);

    // Calculate average price
    double avgPrice = sumY / (double) barsToAnalyze;

    // Calculate slope as percentage of average price
    double slopePercent = (slope * (double) barsToAnalyze) / avgPrice * 100.0;

    // Consider a trend significant if the slope represents more than 3% change
    // over the analysis period (arbitrary threshold, can be adjusted)
    return Math.abs(slopePercent) > 2.5;
  }
}
