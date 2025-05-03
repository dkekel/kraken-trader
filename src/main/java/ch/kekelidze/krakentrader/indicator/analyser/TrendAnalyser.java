package ch.kekelidze.krakentrader.indicator.analyser;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class TrendAnalyser {

  // Minimum required bullish slope
  private static final double BULLISH_SLOPE = 0.1;
  // Moderately strong uptrend
  private static final double MODERATE_SLOPE = 0.2;
  // Very strong uptrend
  private static final double STRONG_SLOPE = 0.4;

  // Minimum required bearish slope
  private static final double BEARISH_SLOPE = -0.1;
  // Moderately strong downtrend
  private static final double MODERATE_DOWNTREND = -0.2;
  // Very strong downtrend
  private static final double STRONG_DOWNTREND = -0.4;

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiIndicator rsiIndicator;
  private final VolumeIndicator volumeIndicator;

  public boolean isDowntrend(List<Bar> data, String symbol, StrategyParameters params) {
    var ma20ma50 = movingAverageIndicator.calculateMovingAverage(data,
        params.movingAverageBuyShortPeriod(), params.movingAverageBuyLongPeriod());
    var endIndex = ma20ma50.endIndex();
    var ma20 = ma20ma50.maShort().getValue(endIndex);
    var ma50 = ma20ma50.maLong().getValue(endIndex);
    log.debug("Downtrend '{}' - MA20: {}, MA50: {}", symbol, ma20, ma50);
    return ma20.isLessThan(ma50);
  }

  public boolean hasBullishDivergence(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    int size = data.size();
    int lookback = params.lookbackPeriod(); // 24 hours of data

    // Need enough data
    if (size < lookback + params.rsiPeriod()) {
      return false;
    }

    // Calculate RSI values for the lookback period
    List<Double> rsiValues = rsiIndicator.calculateRsiValues(data, params.rsiPeriod(), lookback);

    // Find price lows and RSI lows
    List<Integer> priceLowIndexes = findLocalMinima(data, lookback);
    List<Integer> rsiLowIndexes = findLocalMinima(rsiValues);

    // Check for divergence: price making lower lows but RSI making higher lows
    if (priceLowIndexes.size() >= 2 && rsiLowIndexes.size() >= 2) {
      int lastPriceLow = priceLowIndexes.get(priceLowIndexes.size() - 1);
      int prevPriceLow = priceLowIndexes.get(priceLowIndexes.size() - 2);

      int lastRsiLow = rsiLowIndexes.get(rsiLowIndexes.size() - 1);
      int prevRsiLow = rsiLowIndexes.get(rsiLowIndexes.size() - 2);

      double lastLowPrice = data.get(size - lookback + lastPriceLow).getLowPrice().doubleValue();
      double prevLowPrice = data.get(size - lookback + prevPriceLow).getLowPrice().doubleValue();

      double lastLowRsi = rsiValues.get(lastRsiLow);
      double prevLowRsi = rsiValues.get(prevRsiLow);

      boolean pricesMakingLowerLows = lastLowPrice < prevLowPrice;
      boolean rsiMakingHigherLows = lastLowRsi > prevLowRsi;

      return pricesMakingLowerLows && rsiMakingHigherLows;
    }

    return false;
  }

  /**
   * Find local minima (significant lows) in price data
   * @param data Price bar data
   * @param lookback Number of bars to look back
   * @return List of indexes where local minima occur
   */
  private List<Integer> findLocalMinima(List<Bar> data, int lookback) {
    List<Integer> minima = new ArrayList<>();
    int size = data.size();
    int startIndex = size - lookback;

    // Window size for determining significant lows
    int window = 2; // Check 2 bars on each side

    for (int i = startIndex + window; i < size - window; i++) {
      double currentLow = data.get(i).getLowPrice().doubleValue();
      boolean isLocalMinimum = true;

      // Check if this is lower than surrounding bars
      for (int j = i - window; j <= i + window; j++) {
        if (j == i) continue; // Skip comparing with itself

        if (data.get(j).getLowPrice().doubleValue() <= currentLow) {
          isLocalMinimum = false;
          break;
        }
      }

      if (isLocalMinimum) {
        // Store the index relative to the lookback period
        minima.add(i - startIndex);
      }
    }

    return minima;
  }

  /**
   * Find local minima in RSI or other technical indicator values
   * @param values List of indicator values
   * @return List of indexes where local minima occur
   */
  private List<Integer> findLocalMinima(List<Double> values) {
    List<Integer> minima = new ArrayList<>();

    // Window size for determining significant lows
    int window = 2; // Check 2 bars on each side

    for (int i = window; i < values.size() - window; i++) {
      double currentValue = values.get(i);
      boolean isLocalMinimum = true;

      // Check if this is lower than surrounding values
      for (int j = i - window; j <= i + window; j++) {
        if (j == i) continue; // Skip comparing with itself

        if (values.get(j) <= currentValue) {
          isLocalMinimum = false;
          break;
        }
      }

      if (isLocalMinimum) {
        minima.add(i);
      }
    }

    return minima;
  }

  public boolean isBullishSignal(EvaluationContext context, StrategyParameters params) {
    boolean rsiBuySignal = rsiIndicator.isBuySignal(context, params);
    boolean volumeConfirmation = volumeIndicator.isBuySignal(context, params);
    var data = context.getBars();
    double priceSlope = calculateNormalizedSlope(data, params);

    // Check if price sequence is bullish at basic level
    boolean hasBullishSequence = priceSlope > BULLISH_SLOPE;

    // Determine the strength of the uptrend
    boolean hasModerateUptrend = priceSlope > MODERATE_SLOPE;
    boolean hasStrongUptrend = priceSlope > STRONG_SLOPE;

    // Basic price confirmation (current price > previous price)
    boolean priceConfirmation = data.getLast().getClosePrice().doubleValue() >
        data.get(data.size() - 2).getClosePrice().doubleValue();

    log.debug("Bullish '{}' signal evaluation - RSI: {}, Volume: {}, Bullish Sequence: {}, " +
            "Slope: {}%, Moderate: {}, Strong: {}",
        context.getSymbol(), rsiBuySignal, volumeConfirmation, hasBullishSequence,
        priceSlope, hasModerateUptrend, hasStrongUptrend);

    // Tiered decision logic based on uptrend strength:
    if (hasStrongUptrend && priceConfirmation) {
      // For very strong uptrends, just require price confirmation
      return true;
    } else if (hasModerateUptrend && priceConfirmation) {
      // For moderate uptrends, require either RSI or volume (not both)
      return rsiBuySignal || volumeConfirmation;
    } else {
      // For normal scenarios, use original strict criteria
      return (rsiBuySignal || volumeConfirmation) && hasBullishSequence;
    }
  }

  /**
   * Detects bearish divergence between price and RSI, a common signal for potential downtrend reversals
   * Bearish divergence occurs when price makes higher highs but RSI makes lower highs
   *
   * @param context The evaluation context containing market data
   * @param params Strategy parameters
   * @return true if bearish divergence is detected, false otherwise
   */
  public boolean hasBearishDivergence(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    int size = data.size();
    int lookback = params.lookbackPeriod();

    // Need enough data
    if (size < lookback + params.rsiPeriod()) {
      return false;
    }

    // Calculate RSI values for the lookback period
    List<Double> rsiValues = rsiIndicator.calculateRsiValues(data, params.rsiPeriod(), lookback);

    // Find price highs and RSI highs
    List<Integer> priceHighIndexes = findLocalMaxima(data, lookback);
    List<Integer> rsiHighIndexes = findLocalMaxima(rsiValues);

    // Check for divergence: price making higher highs but RSI making lower highs
    if (priceHighIndexes.size() >= 2 && rsiHighIndexes.size() >= 2) {
      int lastPriceHigh = priceHighIndexes.get(priceHighIndexes.size() - 1);
      int prevPriceHigh = priceHighIndexes.get(priceHighIndexes.size() - 2);

      int lastRsiHigh = rsiHighIndexes.get(rsiHighIndexes.size() - 1);
      int prevRsiHigh = rsiHighIndexes.get(rsiHighIndexes.size() - 2);

      double lastHighPrice = data.get(size - lookback + lastPriceHigh).getHighPrice().doubleValue();
      double prevHighPrice = data.get(size - lookback + prevPriceHigh).getHighPrice().doubleValue();

      double lastHighRsi = rsiValues.get(lastRsiHigh);
      double prevHighRsi = rsiValues.get(prevRsiHigh);

      boolean pricesMakingHigherHighs = lastHighPrice > prevHighPrice;
      boolean rsiMakingLowerHighs = lastHighRsi < prevHighRsi;

      log.debug("Bearish divergence check - Price making higher highs: {}, RSI making lower highs: {}",
          pricesMakingHigherHighs, rsiMakingLowerHighs);
      log.debug("Last high price: {}, Previous high price: {}", lastHighPrice, prevHighPrice);
      log.debug("Last high RSI: {}, Previous high RSI: {}", lastHighRsi, prevHighRsi);

      return pricesMakingHigherHighs && rsiMakingLowerHighs;
    }

    return false;
  }

  /**
   * Find local maxima (significant highs) in price data
   * @param data Price bar data
   * @param lookback Number of bars to look back
   * @return List of indexes where local maxima occur
   */
  private List<Integer> findLocalMaxima(List<Bar> data, int lookback) {
    List<Integer> maxima = new ArrayList<>();
    int size = data.size();
    int startIndex = size - lookback;

    // Window size for determining significant highs
    int window = 2; // Check 2 bars on each side

    for (int i = startIndex + window; i < size - window; i++) {
      double currentHigh = data.get(i).getHighPrice().doubleValue();
      boolean isLocalMaximum = true;

      // Check if this is higher than surrounding bars
      for (int j = i - window; j <= i + window; j++) {
        if (j == i) continue; // Skip comparing with itself

        if (data.get(j).getHighPrice().doubleValue() >= currentHigh) {
          isLocalMaximum = false;
          break;
        }
      }

      if (isLocalMaximum) {
        // Store the index relative to the lookback period
        maxima.add(i - startIndex);
      }
    }

    return maxima;
  }

  /**
   * Find local maxima in RSI or other technical indicator values
   * @param values List of indicator values
   * @return List of indexes where local maxima occur
   */
  private List<Integer> findLocalMaxima(List<Double> values) {
    List<Integer> maxima = new ArrayList<>();

    // Window size for determining significant highs
    int window = 2; // Check 2 bars on each side

    for (int i = window; i < values.size() - window; i++) {
      double currentValue = values.get(i);
      boolean isLocalMaximum = true;

      // Check if this is higher than surrounding values
      for (int j = i - window; j <= i + window; j++) {
        if (j == i) continue; // Skip comparing with itself

        if (values.get(j) >= currentValue) {
          isLocalMaximum = false;
          break;
        }
      }

      if (isLocalMaximum) {
        maxima.add(i);
      }
    }

    return maxima;
  }

  public BearishTrendSequence getBearishTrendSequence(EvaluationContext context,
      StrategyParameters params) {
    var data = context.getBars();
    double priceSlope = calculateNormalizedSlope(data, params);

    // Check if price sequence is bearish at basic level
    boolean hasBearishSequence = priceSlope < BEARISH_SLOPE;

    // Determine the strength of the downtrend
    boolean hasModerateDowntrend = priceSlope < MODERATE_DOWNTREND;
    boolean hasStrongDowntrend = priceSlope < STRONG_DOWNTREND;

    // Basic price confirmation (current price < previous price)
    boolean priceConfirmation = data.getLast().getClosePrice().doubleValue() <
        data.get(data.size() - 2).getClosePrice().doubleValue();

    log.debug(
        "Bearish '{}' signal evaluation - Bearish Sequence: {}, Slope: {}%, Moderate: {}, Strong: {}",
        context.getSymbol(), hasBearishSequence, priceSlope, hasModerateDowntrend,
        hasStrongDowntrend);

    return new BearishTrendSequence(hasBearishSequence, hasModerateDowntrend, hasStrongDowntrend,
        priceConfirmation);
  }

  /**
   * Detects bearish signals that indicate potential selling opportunities
   * This is the reverse of the isBullishSignal method used for buy decisions
   *
   * @param context Evaluation context containing market data
   * @param params Strategy parameters
   * @return true if bearish signals are detected, false otherwise
   */
  public boolean isBearishSignal(EvaluationContext context, StrategyParameters params) {
    boolean rsiSellSignal = rsiIndicator.isSellSignal(context, 0, params);
    boolean volumeConfirmation = volumeIndicator.isSellSignal(context, 0, params);

    var bearishSequence = getBearishTrendSequence(context, params);
    boolean hasBearishSequence = bearishSequence.hasBearishSequence;
    boolean hasModerateDowntrend = bearishSequence.hasModerateDowntrend;
    boolean hasStrongDowntrend = bearishSequence.hasStrongDowntrend;
    boolean priceConfirmation = bearishSequence.priceConfirmation;

    log.debug("Bearish '{}' signal evaluation - RSI: {}, Volume: {}",
        context.getSymbol(), rsiSellSignal, volumeConfirmation);

    // Tiered decision logic based on downtrend strength:
    if (hasStrongDowntrend && priceConfirmation) {
      // For very strong downtrends, just require price confirmation
      return true;
    } else if (hasModerateDowntrend && priceConfirmation) {
      // For moderate downtrends, require either RSI or volume (not both)
      return rsiSellSignal || volumeConfirmation;
    } else {
      // For normal scenarios, use original strict criteria
      return (rsiSellSignal || volumeConfirmation) && hasBearishSequence;
    }
  }

  private double calculateNormalizedSlope(List<Bar> data, StrategyParameters parameters) {
    // Only check the relevant lookback period
    int lookbackPeriod = parameters.lookbackPeriod();
    int startIndex = data.size() - lookbackPeriod;

    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < lookbackPeriod; i++) {
      double x = i;
      double y = data.get(startIndex + i).getClosePrice().doubleValue();

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    // Calculate slope of regression line
    double slope = (lookbackPeriod * sumXY - sumX * sumY) /
        (lookbackPeriod * sumX2 - sumX * sumX);

    // Normalize by average price to get a percentage slope
    double avgPrice = sumY / lookbackPeriod;
    double normalizedSlope = (slope / avgPrice) * 100;

    log.debug("Linear regression slope: {}%", normalizedSlope);

    return normalizedSlope;
  }

  /**
   * Checks for consecutive lower highs or lower lows, a bearish pattern
   * @param data Price bar data
   * @param parameters Params containing the number of consecutive bars to check
   * @return true if found consecutive lower highs or lower lows
   */
  public boolean hasConsecutiveLowerHighsOrLows(List<Bar> data, StrategyParameters parameters) {
    var count = parameters.bearishPatternLookbackPeriod();
    if (data.size() < count + 1) return false;

    int size = data.size();
    boolean consecutiveLowerHighs = true;
    boolean consecutiveLowerLows = true;

    // Check for lower highs
    for (int i = size - count; i < size - 1; i++) {
      double currentHigh = data.get(i).getHighPrice().doubleValue();
      double nextHigh = data.get(i + 1).getHighPrice().doubleValue();
      if (nextHigh >= currentHigh) {
        consecutiveLowerHighs = false;
        break;
      }
    }

    // Check for lower lows
    for (int i = size - count; i < size - 1; i++) {
      double currentLow = data.get(i).getLowPrice().doubleValue();
      double nextLow = data.get(i + 1).getLowPrice().doubleValue();
      if (nextLow >= currentLow) {
        consecutiveLowerLows = false;
        break;
      }
    }

    return consecutiveLowerHighs || consecutiveLowerLows;
  }

  @Getter
  @RequiredArgsConstructor
  public static class BullishTrendSequence {
    final boolean hasBullishSequence;
    final boolean hasModerateUptrend;
    final boolean hasStrongUptrend;
    final boolean priceConfirmation;
  }

  @Getter
  @RequiredArgsConstructor
  public static class BearishTrendSequence {
    final boolean hasBearishSequence;
    final boolean hasModerateDowntrend;
    final boolean hasStrongDowntrend;
    final boolean priceConfirmation;
  }
}
