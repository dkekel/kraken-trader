package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatilityIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    double volatilityPercentage = calculateVolatilityPercentage(data, params);

    // Check standard threshold first
    if (volatilityPercentage < params.lowVolatilityThreshold()) {
      log.debug("Volatility OK (standard check): {}% < threshold {}%",
          volatilityPercentage, params.lowVolatilityThreshold());
      return true;
    }

    // Calculate more context-aware values
    double historicalVolatility = calculateHistoricalVolatility(data, params);
    double trendStrength = calculateTrendStrength(data);

    // Calculate a volatility tolerance level based on trend strength
    // Stronger uptrends can tolerate more volatility
    double dynamicVolatilityThreshold = params.lowVolatilityThreshold() *
        (1 + (0.5 * trendStrength));

    // For strong trends, we can accept higher volatility
    if (trendStrength > 0.6 && volatilityPercentage < dynamicVolatilityThreshold) {
      log.debug("Volatility OK (dynamic threshold): {}% < adjusted threshold {}% (trend strength: {})",
          volatilityPercentage, dynamicVolatilityThreshold, trendStrength);
      return true;
    }

    // Also check volatility relative to the asset's historical patterns
    // If current volatility isn't significantly higher than the historical average, it's acceptable
    if (volatilityPercentage < historicalVolatility * 1.3) {
      log.debug("Volatility OK (historical comparison): {}% < historical {}% * 1.3",
          volatilityPercentage, historicalVolatility);
      return true;
    }

    log.debug("Volatility too high: {}% exceeds all thresholds", volatilityPercentage);
    return false;
  }

  /**
   * Calculates historical average volatility for context
   */
  private double calculateHistoricalVolatility(List<Bar> data, StrategyParameters params) {
    int lookbackDays = 5; // Look back 5 days
    int periodsPerDay = 24; // Assuming hourly data
    int periods = Math.min(lookbackDays * periodsPerDay, data.size() - params.atrPeriod());

    if (periods <= 0) return params.lowVolatilityThreshold(); // Fallback

    double sum = 0;
    int count = 0;

    for (int i = 0; i < periods; i++) {
      int startIdx = data.size() - periods - params.atrPeriod() + i;
      if (startIdx < 0) continue;

      List<Bar> subset = data.subList(startIdx, startIdx + params.atrPeriod());
      double atr = atrAnalyser.calculateATR(subset, params.atrPeriod());
      double price = subset.get(subset.size() - 1).getClosePrice().doubleValue();
      double volatility = (atr / price) * 100;

      sum += volatility;
      count++;
    }

    return count > 0 ? sum / count : params.lowVolatilityThreshold();
  }

  /**
   * Calculates the strength of the current trend (0-1)
   * Higher values indicate stronger trends
   */
  private double calculateTrendStrength(List<Bar> data) {
    if (data.size() < 10) return 0.0;

    // Use last 10 bars to calculate trend strength
    List<Bar> recentBars = data.subList(data.size() - 10, data.size());

    // Calculate linear regression slope
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < recentBars.size(); i++) {
      double x = i;
      double y = recentBars.get(i).getClosePrice().doubleValue();

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    int n = recentBars.size();
    double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

    // Normalize by average price to get percentage slope
    double avgPrice = sumY / n;
    double normalizedSlope = (slope / avgPrice) * 100;

    // Convert to 0-1 strength (0.5% slope per bar is quite strong in crypto)
    double trendStrength = Math.min(1.0, Math.max(0.0, normalizedSlope / 0.5));

    log.debug("Trend strength: {} (normalized slope: {}%)", trendStrength, normalizedSlope);
    return trendStrength;
  }

  @Override
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    double volatilityPercentage = calculateVolatilityPercentage(data, params);
    return volatilityPercentage > params.highVolatilityThreshold();
  }

  public double calculateVolatilityPercentage(List<Bar> data, StrategyParameters parameters) {
    double atr = atrAnalyser.calculateATR(data, parameters.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    double volatilityPercentage = (atr / currentPrice) * 100;
    log.debug(
        "Volatility calculation - ATR: {}, Current price: {}, Volatility: {}%, Threshold: {}%",
        atr, currentPrice, volatilityPercentage, parameters.lowVolatilityThreshold());
    return volatilityPercentage;
  }
}
