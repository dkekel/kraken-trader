package ch.kekelidze.krakentrader.optimize.service;

import ch.kekelidze.krakentrader.optimize.model.RegimeSegment;
import ch.kekelidze.krakentrader.optimize.model.RegimeType;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

/**
 * The DataMassageService processes historical trading data to identify market regimes and calculate
 * important metrics such as volatility, trend strength, and price averages. This service supports
 * financial analysis by segmenting data into meaningful contexts for evaluation.
 * <p>
 * The class provides methods to classify and refine market regimes, calculate statistical measures,
 * and create data contexts for financial modeling and optimization.
 * <p>
 * Class Fields: - MIN_REGIME_LENGTH: A constant representing the minimum duration (in data points)
 * for a market regime to be considered valid. - log: Logger instance for tracing and debugging
 * purposes.
 * <p>
 * Key Features: - Creation of multi-regime evaluation contexts from historical data. - Calculation
 * of volatility, trend strength, and other statistical measures. - Segmentation of market data into
 * identifiable regimes and time-based intervals. - Identification and refinement of nuanced market
 * regimes.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataMassageService {

  // For 5-minute candles, calculate how many candles represent approximately 2 months
  // 5-minute candles: 12 per hour * 24 hours * 60 days = ~17,280 candles for 2 months
  final int MIN_REGIME_LENGTH = 17_280;

  /**
   * Creates multiple evaluation contexts representing different market regimes from a single
   * dataset, ensuring each regime spans at least several months to provide adequate optimization
   * samples.
   *
   * @param coinPair The trading pair being analyzed
   * @param period   The time period (interval) of the data in minutes
   * @param data     The complete historical price data (5-minute candles)
   * @return A list of evaluation contexts, each representing a different market regime
   */
  public List<EvaluationContext> createMultiRegimeContexts(String coinPair, int period,
      List<Bar> data) {
    UUID uuid = UUID.randomUUID();
    List<EvaluationContext> regimeContexts = new ArrayList<>();

    // Ensure we have enough data to work with
    if (data.size() < 5000) { // Minimum required data for reliable analysis
      log.warn("Insufficient data for {} to identify multiple regimes (size: {})", coinPair,
          data.size());
      regimeContexts.add(EvaluationContext.builder()
          .symbol(coinPair + "_" + uuid)
          .period(period)
          .bars(data)
          .build());
      return regimeContexts;
    }

    log.info("Creating market regime contexts for {} with {} data points", coinPair, data.size());

    // Extract volatility and trend characteristics
    Map<Integer, Double> volatilityMap = calculateVolatilityForLongPeriods(data);
    Map<Integer, Double> trendStrengthMap = calculateTrendStrengthForLongPeriods(data);

    // Divide the data into quarters (approximately 6 months each for 2 years of data)
    List<RegimeSegment> regimeSegments = identifyRegimesByQuarters(data, volatilityMap,
        trendStrengthMap);

    // If we need more granularity within each quarter
    if (regimeSegments.size() <= 2) {
      log.debug("Found only {} major regimes, attempting to find sub-regimes",
          regimeSegments.size());
      regimeSegments = refineRegimeSegments(data, volatilityMap, trendStrengthMap,
          MIN_REGIME_LENGTH);
    }

    // Create a context for each significant regime segment
    for (RegimeSegment segment : regimeSegments) {
      if (segment.length() >= MIN_REGIME_LENGTH) {
        List<Bar> segmentData = data.subList(segment.startIndex(), segment.endIndex() + 1);

        // Calculate some statistics to include in metadata
        double volatility = calculateAverageVolatility(segmentData);
        double avgPrice = calculateAveragePrice(segmentData);

        EvaluationContext context = EvaluationContext.builder()
            .symbol(coinPair + "_" + segment.regimeType().name() + "_" + uuid.toString())
            .period(period)
            .bars(segmentData)
            .metadata(Map.of(
                "regimeType", segment.regimeType().name(),
                "volatility", String.format("%.2f", volatility),
                "avgPrice", String.format("%.2f", avgPrice),
                "startDate", segmentData.getFirst().getEndTime().toString(),
                "endDate", segmentData.getLast().getEndTime().toString()
            ))
            .build();

        regimeContexts.add(context);

        log.info("Created regime context: {} with {} bars of type {} from {} to {}",
            context.getSymbol(),
            segmentData.size(),
            segment.regimeType(),
            segmentData.getFirst().getEndTime(),
            segmentData.getLast().getEndTime());
      } else {
        log.debug("Skipping regime segment of length {} (min required: {})",
            segment.length(), MIN_REGIME_LENGTH);
      }
    }

    // If we couldn't identify enough regimes with adequate data, fall back to time-based segmentation
    if (regimeContexts.size() < 2) {
      log.warn("Insufficient regime contexts identified, falling back to time-based segmentation");
      regimeContexts = createTimeBasedSegments(coinPair, period, data, MIN_REGIME_LENGTH);
    }

    return regimeContexts;
  }

  /**
   * Calculate volatility over longer periods to smooth out short-term noise
   */
  private Map<Integer, Double> calculateVolatilityForLongPeriods(List<Bar> data) {
    Map<Integer, Double> volatility = new HashMap<>();
    // For 5-minute candles, use a window of approximately 1 week
    // 12 candles per hour * 24 hours * 7 days = 2016 candles
    int window = 2016;

    // Calculate on a weekly basis to reduce computation and smooth out noise
    int step = 288; // 1 day worth of 5-minute candles

    for (int i = window; i < data.size(); i += step) {
      List<Bar> windowData = data.subList(i - window, i);

      // Calculate price-based volatility as standard deviation / average price
      double avgPrice = windowData.stream()
          .mapToDouble(bar -> bar.getClosePrice().doubleValue())
          .average()
          .orElse(0);

      double volatilityValue = calculatePriceVolatility(windowData, avgPrice);

      // Store the volatility value for each index in this window
      for (int j = i - step; j < i; j++) {
        if (j >= window) {
          volatility.put(j, volatilityValue);
        }
      }
    }

    return volatility;
  }

  /**
   * Calculate price volatility for a window of data
   */
  private double calculatePriceVolatility(List<Bar> windowData, double avgPrice) {
    double sumSquaredDiff = windowData.stream()
        .mapToDouble(bar -> Math.pow(bar.getClosePrice().doubleValue() - avgPrice, 2))
        .sum();

    double stdDev = Math.sqrt(sumSquaredDiff / windowData.size());
    return stdDev / avgPrice * 100.0; // as percentage of average price
  }

  /**
   * Calculate trend strength over longer periods
   */
  private Map<Integer, Double> calculateTrendStrengthForLongPeriods(List<Bar> data) {
    Map<Integer, Double> trendStrength = new HashMap<>();
    // Use approximately one week of data
    int window = 2016;
    int step = 288; // Calculate daily

    for (int i = window; i < data.size(); i += step) {
      // Get the window of data
      List<Bar> windowData = data.subList(i - window, i);

      // Calculate a simplified directional strength metric
      // Look at the linear regression slope of prices
      double trendValue = calculateLinearRegressionSlope(windowData);

      // Store the trend value for each index in this window
      for (int j = i - step; j < i; j++) {
        if (j >= window) {
          trendStrength.put(j, trendValue);
        }
      }
    }

    return trendStrength;
  }

  /**
   * Calculate the slope of the linear regression line for price data as a measure of trend strength
   * and direction
   */
  private double calculateLinearRegressionSlope(List<Bar> data) {
    int n = data.size();
    double sumX = 0;
    double sumY = 0;
    double sumXY = 0;
    double sumX2 = 0;

    for (int i = 0; i < n; i++) {
      double x = i;
      double y = data.get(i).getClosePrice().doubleValue();

      sumX += x;
      sumY += y;
      sumXY += x * y;
      sumX2 += x * x;
    }

    // Calculate slope of the regression line
    double slope = (n * sumXY - sumX * sumY) / (n * sumX2 - sumX * sumX);

    // Normalize by average price to get a percentage
    double avgPrice = sumY / n;

    return (slope * n) / avgPrice * 100.0; // Adjusted to represent % change over the period
  }

  /**
   * Identify market regimes by dividing the data into quarters and analyzing each quarter
   */
  private List<RegimeSegment> identifyRegimesByQuarters(List<Bar> data,
      Map<Integer, Double> volatilityMap, Map<Integer, Double> trendStrengthMap) {

    List<RegimeSegment> segments = new ArrayList<>();
    int totalBars = data.size();

    // Divide data into quarters (approximately 6 months each for 2 years of data)
    int quarterSize = totalBars / 4;

    for (int i = 0; i < 4; i++) {
      int startIndex = i * quarterSize;
      int endIndex = (i == 3) ? totalBars - 1 : (i + 1) * quarterSize - 1;

      // Skip if not enough data
      if (startIndex >= endIndex || startIndex < 0 || endIndex >= totalBars) {
        continue;
      }

      // Calculate average volatility and trend strength for this quarter
      double avgVolatility = 0.0;
      double avgTrendStrength = 0.0;
      int count = 0;

      for (int j = startIndex; j <= endIndex; j++) {
        if (volatilityMap.containsKey(j) && trendStrengthMap.containsKey(j)) {
          avgVolatility += volatilityMap.get(j);
          avgTrendStrength += trendStrengthMap.get(j);
          count++;
        }
      }

      if (count > 0) {
        avgVolatility /= count;
        avgTrendStrength /= count;

        // Classify the regime based on the average metrics
        RegimeType regimeType = classifyRegimeBasedOnAverages(avgVolatility, avgTrendStrength);

        segments.add(new RegimeSegment(startIndex, endIndex, regimeType));

        log.debug("Quarter {} classified as {} regime (vol: {}, trend: {})",
            i + 1, regimeType, avgVolatility, avgTrendStrength);
      }
    }

    return segments;
  }

  /**
   * Classify the market regime based on average volatility and trend strength
   */
  private RegimeType classifyRegimeBasedOnAverages(double volatility, double trendStrength) {
    // Thresholds based on normalized values
    boolean isHighVolatility = volatility > 2.0;
    boolean isLowVolatility = volatility < 0.75;
    boolean isStrongTrend = Math.abs(trendStrength) > 0.15;
    boolean isUptrend = trendStrength > 0;

    if (isHighVolatility) {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.VOLATILE_UPTREND : RegimeType.VOLATILE_DOWNTREND;
      } else {
        return RegimeType.VOLATILE_RANGING;
      }
    } else if (isLowVolatility) {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.CALM_UPTREND : RegimeType.CALM_DOWNTREND;
      } else {
        return RegimeType.CALM_RANGING;
      }
    } else {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.NORMAL_UPTREND : RegimeType.NORMAL_DOWNTREND;
      } else {
        return RegimeType.NORMAL_RANGING;
      }
    }
  }

  /**
   * Refine regime segments to find more nuanced regimes within large segments
   */
  private List<RegimeSegment> refineRegimeSegments(List<Bar> data,
      Map<Integer, Double> volatilityMap, Map<Integer, Double> trendStrengthMap,
      int minRegimeLength) {

    List<RegimeSegment> segments = new ArrayList<>();

    // Determine volatility and trend strength thresholds
    double[] volatilities = volatilityMap.values().stream().mapToDouble(d -> d).toArray();
    double[] trends = trendStrengthMap.values().stream().mapToDouble(d -> d).toArray();

    double medianVolatility = calculateMedian(volatilities);
    double medianTrendStrength = calculateMedian(trends);

    double highVolThreshold = medianVolatility * 1.5;
    double lowVolThreshold = medianVolatility * 0.5;
    double strongTrendThreshold = Math.abs(medianTrendStrength) * 1.3;

    // Find indices where we have both volatility and trend data
    int startSearchIdx = 0;
    for (int i = 0; i < data.size(); i++) {
      if (volatilityMap.containsKey(i) && trendStrengthMap.containsKey(i)) {
        startSearchIdx = i;
        break;
      }
    }

    // Initialize the first regime
    RegimeType currentRegime = classifyRegime(
        volatilityMap.getOrDefault(startSearchIdx, 0.0),
        trendStrengthMap.getOrDefault(startSearchIdx, 0.0),
        highVolThreshold, lowVolThreshold, strongTrendThreshold);

    int currentSegmentStart = startSearchIdx;
    RegimeType prevRegime = currentRegime;
    int regimeStableCount = 0;

    // Scan through data to identify regime changes, requiring stability
    // We use a sliding window to determine if a regime change is stable
    int windowSize = 2880; // about 10 days of 5-minute candles

    for (int i = startSearchIdx + windowSize; i < data.size(); i += windowSize) {
      // Calculate the dominant regime in this window
      Map<RegimeType, Integer> regimeCounts = new HashMap<>();

      for (int j = i - windowSize; j < i; j += 144) { // Sample every 144 candles (~12 hours)
        if (volatilityMap.containsKey(j) && trendStrengthMap.containsKey(j)) {
          RegimeType regime = classifyRegime(
              volatilityMap.get(j),
              trendStrengthMap.get(j),
              highVolThreshold, lowVolThreshold, strongTrendThreshold);

          regimeCounts.put(regime, regimeCounts.getOrDefault(regime, 0) + 1);
        }
      }

      // Find the dominant regime
      RegimeType dominantRegime = regimeCounts.entrySet().stream()
          .max(Map.Entry.comparingByValue())
          .map(Map.Entry::getKey)
          .orElse(currentRegime);

      // If we detect a regime change that persists
      if (dominantRegime != currentRegime) {
        regimeStableCount = 0;
        prevRegime = currentRegime;
        currentRegime = dominantRegime;
      } else {
        regimeStableCount++;
      }

      // If we have a stable new regime, create a segment for the previous regime
      if (regimeStableCount == 1) {
        int segmentEnd = i - windowSize - 1;
        // Only add if segment is long enough
        if (segmentEnd - currentSegmentStart >= minRegimeLength) {
          segments.add(new RegimeSegment(currentSegmentStart, segmentEnd, prevRegime));
          log.debug("Added regime segment: {} from {} to {} (length: {})",
              prevRegime, currentSegmentStart, segmentEnd, segmentEnd - currentSegmentStart + 1);

          currentSegmentStart = segmentEnd + 1;
        }
      }
    }

    // Add the final segment
    if (data.size() - 1 - currentSegmentStart >= minRegimeLength) {
      segments.add(new RegimeSegment(currentSegmentStart, data.size() - 1, currentRegime));
      log.debug("Added final regime segment: {} from {} to {} (length: {})",
          currentRegime, currentSegmentStart, data.size() - 1, data.size() - currentSegmentStart);
    }

    log.info("Identified {} refined market regime segments", segments.size());
    return segments;
  }

  /**
   * Classify the market regime based on volatility and trend strength metrics
   */
  private RegimeType classifyRegime(double volatility, double trendStrength,
      double highVolThreshold, double lowVolThreshold, double strongTrendThreshold) {

    boolean isHighVolatility = volatility > highVolThreshold;
    boolean isLowVolatility = volatility < lowVolThreshold;
    boolean isStrongTrend = Math.abs(trendStrength) > strongTrendThreshold;
    boolean isUptrend = trendStrength > 0;

    if (isHighVolatility) {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.VOLATILE_UPTREND : RegimeType.VOLATILE_DOWNTREND;
      } else {
        return RegimeType.VOLATILE_RANGING;
      }
    } else if (isLowVolatility) {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.CALM_UPTREND : RegimeType.CALM_DOWNTREND;
      } else {
        return RegimeType.CALM_RANGING;
      }
    } else {
      if (isStrongTrend) {
        return isUptrend ? RegimeType.NORMAL_UPTREND : RegimeType.NORMAL_DOWNTREND;
      } else {
        return RegimeType.NORMAL_RANGING;
      }
    }
  }

  /**
   * Calculate median of an array of values
   */
  private double calculateMedian(double[] values) {
    if (values.length == 0) {
      return 0;
    }

    Arrays.sort(values);
    int middle = values.length / 2;
    if (values.length % 2 == 0) {
      return (values[middle - 1] + values[middle]) / 2.0;
    } else {
      return values[middle];
    }
  }

  /**
   * Create time-based segments as a fallback or complement to regime-based segmentation
   */
  private List<EvaluationContext> createTimeBasedSegments(String coinPair, int period,
      List<Bar> data, int minSegmentSize) {
    UUID uuid = UUID.randomUUID();
    List<EvaluationContext> segments = new ArrayList<>();

    // Divide the data into 4 chronological segments (approximately 6 months each)
    int segmentSize = data.size() / 4;

    for (int i = 0; i < 4; i++) {
      int startIdx = i * segmentSize;
      int endIdx = (i == 3) ? data.size() : (i + 1) * segmentSize;

      // If segment is too small, skip it
      if (endIdx - startIdx < minSegmentSize) {
        continue;
      }

      List<Bar> segmentData = data.subList(startIdx, endIdx);

      // Get time range for this segment for better context
      ZonedDateTime startTime = segmentData.getFirst().getEndTime();
      ZonedDateTime endTime = segmentData.getLast().getEndTime();

      EvaluationContext context = EvaluationContext.builder()
          .symbol(coinPair + "_period_" + startTime.getYear()
              + "_" + startTime.getMonthValue()
              + "_to_" + endTime.getMonthValue()
              + "_" + uuid)
          .period(period)
          .bars(segmentData)
          .metadata(Map.of(
              "timeSegment", "Q" + (i + 1),
              "startDate", startTime.toString(),
              "endDate", endTime.toString()
          ))
          .build();

      segments.add(context);

      log.info("Created time-based segment {} with {} bars from {} to {}",
          context.getSymbol(), segmentData.size(), startTime, endTime);
    }

    return segments;
  }

  /**
   * Calculate average volatility for a data segment
   */
  private double calculateAverageVolatility(List<Bar> data) {
    double sum = 0;

    // Use a 20-day window for shorter-term volatility calculation
    int window = Math.min(2880, data.size() / 3);
    if (data.size() <= window) {
      return 0;
    }

    for (int i = window; i < data.size(); i += 720) { // Sample every 720 candles (2.5 days)
      List<Bar> windowData = data.subList(i - window, i);
      double avgPrice = windowData.stream()
          .mapToDouble(bar -> bar.getClosePrice().doubleValue())
          .average()
          .orElse(0);

      sum += calculatePriceVolatility(windowData, avgPrice);
    }

    return sum / ((double) (data.size() - window) / 720 + 1);
  }

  /**
   * Calculate average price for a data segment
   */
  private double calculateAveragePrice(List<Bar> data) {
    return data.stream()
        .mapToDouble(bar -> bar.getClosePrice().doubleValue())
        .average()
        .orElse(0);
  }
}
