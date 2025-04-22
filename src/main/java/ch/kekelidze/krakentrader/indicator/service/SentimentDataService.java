package ch.kekelidze.krakentrader.indicator.service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.nd4j.shade.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class SentimentDataService {

  private final RestTemplate restTemplate;

  // Cache sentiment data to avoid excessive API calls during backtesting
  private final Map<String, Map<Long, Double>> sentimentCache = new ConcurrentHashMap<>();

  @Value("${sentiment.lunarcrush.api-key:}")
  private String lunarCrushApiKey;

  @Value("${sentiment.cryptocompare.api-key:}")
  private String cryptoCompareApiKey;

  private static final double DEFAULT_SENTIMENT_SCORE = 0.0; // Neutral sentiment
  private static final String FEAR_GREED_URL = "https://api.alternative.me/fng/";
  private static final String LUNARCRUSH_URL = "https://lunarcrush.com/api3/coins";
  private static final String CRYPTOCOMPARE_URL =
      "https://min-api.cryptocompare.com/data/v2/social/coin/histo/day";

  /**
   * Gets the combined sentiment score for an asset at a specific timestamp Ranges from -100
   * (extremely bearish) to +100 (extremely bullish)
   *
   * @param asset     Asset symbol (e.g., "BTC")
   * @param timestamp Unix timestamp in seconds
   * @return Sentiment score between -100 and 100
   */
  public double getSentimentScore(String asset, long timestamp) {
    // Check cache first
    if (sentimentCache.containsKey(asset) && sentimentCache.get(asset).containsKey(timestamp)) {
      return sentimentCache.get(asset).get(timestamp);
    }

    try {
      // Fetch sentiment from multiple sources
      double socialMediaSentiment = fetchSocialMediaSentiment(asset, timestamp);
      double newsSentiment = fetchNewsSentiment(asset, timestamp);
      double fearAndGreedIndex = fetchFearAndGreedIndex(timestamp);

      // Combine with weights (adjust weights based on reliability of each source)
      double combinedScore = (socialMediaSentiment * 0.4) +
          (newsSentiment * 0.4) +
          (fearAndGreedIndex * 0.2);

      // Cache the result
      sentimentCache.computeIfAbsent(asset, k -> new HashMap<>())
          .put(timestamp, combinedScore);

      return combinedScore;
    } catch (Exception e) {
      log.error("Error fetching sentiment data for {}: {}", asset, e.getMessage());
      return DEFAULT_SENTIMENT_SCORE;
    }
  }

  /**
   * Gets the latest sentiment score for an asset
   *
   * @param asset Asset symbol (e.g., "BTC")
   * @return Latest sentiment score
   */
  public double getSentimentScore(String asset) {
    return getSentimentScore(asset, Instant.now().getEpochSecond());
  }

  /**
   * Fetches social media sentiment from LunarCrush
   *
   * @param asset     Asset symbol
   * @param timestamp Unix timestamp
   * @return Sentiment score (-100 to 100)
   */
  private double fetchSocialMediaSentiment(String asset, long timestamp) {
    try {
      String url = LUNARCRUSH_URL + "/" + asset.toLowerCase() +
          "?key=" + lunarCrushApiKey +
          "&data=social&time=" + timestamp;

      ResponseEntity<LunarCrushResponse> response =
          restTemplate.getForEntity(url, LunarCrushResponse.class);

      if (response.getBody() != null && response.getBody().getData() != null &&
          !response.getBody().getData().isEmpty()) {

        LunarCrushData data = response.getBody().getData().get(0);

        // Convert to our scale (-100 to 100)
        // LunarCrush sentiment ranges from 0 to 100 where 50 is neutral
        return (data.getSocialScore() - 50) * 2;
      }

      return DEFAULT_SENTIMENT_SCORE;
    } catch (RestClientException e) {
      log.warn("Failed to fetch social media sentiment from LunarCrush: {}", e.getMessage());
      return DEFAULT_SENTIMENT_SCORE;
    }
  }

  /**
   * Fetches news sentiment from CryptoCompare
   *
   * @param asset     Asset symbol
   * @param timestamp Unix timestamp
   * @return Sentiment score (-100 to 100)
   */
  private double fetchNewsSentiment(String asset, long timestamp) {
    try {
      String url = CRYPTOCOMPARE_URL +
          "?fsym=" + asset.toUpperCase() +
          "&api_key=" + cryptoCompareApiKey +
          "&limit=1" +
          "&toTs=" + timestamp;

      ResponseEntity<CryptoCompareResponse> response =
          restTemplate.getForEntity(url, CryptoCompareResponse.class);

      if (response.getBody() != null &&
          response.getBody().getData() != null &&
          !response.getBody().getData().isEmpty()) {

        CryptoCompareData data = response.getBody().getData().get(0);

        // CryptoCompare returns sentiment as negative/neutral/positive comments
        int totalComments = data.getComments();
        if (totalComments == 0) {
          return DEFAULT_SENTIMENT_SCORE;
        }

        double positivePercent = (double) data.getPositiveComments() / totalComments;
        double negativePercent = (double) data.getNegativeComments() / totalComments;

        // Convert to scale from -100 to 100
        return (positivePercent - negativePercent) * 100;
      }

      return DEFAULT_SENTIMENT_SCORE;
    } catch (RestClientException e) {
      log.warn("Failed to fetch news sentiment from CryptoCompare: {}", e.getMessage());
      return DEFAULT_SENTIMENT_SCORE;
    }
  }

  /**
   * Fetches the Fear & Greed index for crypto market
   *
   * @param timestamp Unix timestamp
   * @return Fear & Greed score (-100 to 100)
   */
  private double fetchFearAndGreedIndex(long timestamp) {
    try {
      String date = convertTimestampToDate(timestamp);
      String url = FEAR_GREED_URL + "?date_format=us&format=json&date=" + date;

      ResponseEntity<FearGreedResponse> response =
          restTemplate.getForEntity(url, FearGreedResponse.class);

      if (response.getBody() != null &&
          response.getBody().getData() != null &&
          !response.getBody().getData().isEmpty()) {

        FearGreedData data = response.getBody().getData().get(0);

        // Convert from 0-100 scale to -100 to 100 scale
        // 0-25: Extreme Fear, 25-50: Fear, 50-75: Greed, 75-100: Extreme Greed
        return (data.getValue() - 50) * 2;
      }

      return DEFAULT_SENTIMENT_SCORE;
    } catch (RestClientException e) {
      log.warn("Failed to fetch Fear & Greed Index: {}", e.getMessage());
      return DEFAULT_SENTIMENT_SCORE;
    }
  }

  /**
   * Loads historical sentiment data for backtesting
   *
   * @param asset           Asset symbol
   * @param startTime       Start timestamp
   * @param endTime         End timestamp
   * @param intervalSeconds Interval between data points in seconds
   */
  public void loadHistoricalSentimentData(String asset, long startTime, long endTime,
      long intervalSeconds) {
    log.info("Loading historical sentiment data for {} from {} to {}",
        asset, startTime, endTime);

    // Load data from Fear & Greed Index (market-wide sentiment)
    loadHistoricalFearGreedData(asset, startTime, endTime, intervalSeconds);

    // Load asset-specific sentiment
    loadHistoricalAssetSentiment(asset, startTime, endTime, intervalSeconds);

    log.info("Loaded {} sentiment data points for {}",
        sentimentCache.getOrDefault(asset, new HashMap<>()).size(), asset);
  }

  /**
   * Loads historical Fear & Greed data
   */
  private void loadHistoricalFearGreedData(String asset, long startTime, long endTime,
      long intervalSeconds) {
    try {
      String url = FEAR_GREED_URL + "?limit=0&format=json"; // Get all historical data

      ResponseEntity<FearGreedResponse> response =
          restTemplate.getForEntity(url, FearGreedResponse.class);

      if (response.getBody() != null && response.getBody().getData() != null) {
        Map<String, Double> dateToScore = new HashMap<>();

        // Map dates to scores
        for (FearGreedData data : response.getBody().getData()) {
          String dateStr = data.getTimestamp();
          Instant instant = Instant.parse(dateStr);
          long timestamp = instant.getEpochSecond();

          // Convert from 0-100 scale to -100 to 100 scale
          double score = (data.getValue() - 50) * 2;

          if (timestamp >= startTime && timestamp <= endTime) {
            dateToScore.put(dateStr.substring(0, 10), score); // Use only the date part
          }
        }

        // Now map each timestamp to the appropriate day's score
        for (long timestamp = startTime; timestamp <= endTime; timestamp += intervalSeconds) {
          String date = convertTimestampToDate(timestamp);

          if (dateToScore.containsKey(date)) {
            sentimentCache.computeIfAbsent(asset, k -> new HashMap<>())
                .put(timestamp, dateToScore.get(date));
          }
        }
      }
    } catch (Exception e) {
      log.error("Error loading historical Fear & Greed data: {}", e.getMessage());
    }
  }

  /**
   * Loads historical asset-specific sentiment data
   */
  private void loadHistoricalAssetSentiment(String asset, long startTime, long endTime,
      long intervalSeconds) {
    // For a real implementation, you would fetch data from a historical
    // sentiment API or database. Here, we'll approximate it based on
    // available historical prices for backtesting purposes.

    // This is where you would make API calls to get historical sentiment
    // data from providers like Santiment, LunarCrush, or others.

    // For this example, we'll just create a basic implementation that could
    // be expanded for actual API integration.
    try {
      // Generate sampling timestamps (e.g., daily intervals)
      for (long timestamp = startTime; timestamp <= endTime; timestamp += 86400) {
        // Make API calls for specific timestamps if you have historical API access
        double socialScore = fetchHistoricalSocialSentiment(asset, timestamp);
        double newsScore = fetchHistoricalNewsSentiment(asset, timestamp);

        // Combine the scores
        double combinedScore = (socialScore * 0.5) + (newsScore * 0.5);

        // Save to cache
        sentimentCache.computeIfAbsent(asset, k -> new HashMap<>())
            .put(timestamp, combinedScore);
      }

      // Now interpolate for any missing intervals in the cache
      interpolateSentimentData(asset, startTime, endTime, intervalSeconds);
    } catch (Exception e) {
      log.error("Error loading historical asset sentiment data: {}", e.getMessage());
    }
  }

  /**
   * Fetch historical social sentiment (to be implemented with actual API)
   */
  private double fetchHistoricalSocialSentiment(String asset, long timestamp) {
    // In a real implementation, you would call an API that provides historical sentiment
    // For backtesting purposes, we can simulate a sentiment score

    // This is just a placeholder; replace with actual API call for production use
    return Math.sin((timestamp / 86400.0) * 0.1) * 50; // Simple oscillation for demo
  }

  /**
   * Fetch historical news sentiment (to be implemented with actual API)
   */
  private double fetchHistoricalNewsSentiment(String asset, long timestamp) {
    // In a real implementation, you would call an API that provides historical news sentiment
    // For backtesting purposes, we can simulate a sentiment score

    // This is just a placeholder; replace with actual API call for production use
    return Math.cos((timestamp / 86400.0) * 0.15) * 40; // Simple oscillation for demo
  }

  /**
   * Interpolate sentiment data for missing timestamps
   */
  private void interpolateSentimentData(String asset, long startTime, long endTime,
      long intervalSeconds) {
    if (!sentimentCache.containsKey(asset)) {
      return;
    }

    Map<Long, Double> assetSentiment = sentimentCache.get(asset);

    // Get all available timestamps
    List<Long> availableTimes = new ArrayList<>(assetSentiment.keySet());
    Collections.sort(availableTimes);

    if (availableTimes.isEmpty()) {
      return;
    }

    // For each interval, find or interpolate sentiment value
    for (long timestamp = startTime; timestamp <= endTime; timestamp += intervalSeconds) {
      if (assetSentiment.containsKey(timestamp)) {
        continue; // Already have data
      }

      // Find nearest timestamps before and after
      Long before = null;
      Long after = null;

      for (Long time : availableTimes) {
        if (time <= timestamp && (before == null || time > before)) {
          before = time;
        }
        if (time >= timestamp && (after == null || time < after)) {
          after = time;
        }
      }

      // Interpolate or use nearest value
      double sentimentValue;
      if (before != null && after != null) {
        // Linear interpolation
        double beforeValue = assetSentiment.get(before);
        double afterValue = assetSentiment.get(after);
        double ratio = (double) (timestamp - before) / (after - before);
        sentimentValue = beforeValue + ratio * (afterValue - beforeValue);
      } else if (before != null) {
        sentimentValue = assetSentiment.get(before);
      } else if (after != null) {
        sentimentValue = assetSentiment.get(after);
      } else {
        sentimentValue = DEFAULT_SENTIMENT_SCORE;
      }

      assetSentiment.put(timestamp, sentimentValue);
    }
  }

  /**
   * Converts a Unix timestamp to a date string (YYYY-MM-DD)
   */
  private String convertTimestampToDate(long timestamp) {
    LocalDateTime dateTime = LocalDateTime.ofInstant(
        Instant.ofEpochSecond(timestamp), ZoneId.systemDefault());
    return dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE);
  }

  // Response classes for API calls

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class FearGreedResponse {

    private List<FearGreedData> data;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class FearGreedData {

    private String timestamp;
    private int value;
    private String value_classification;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class LunarCrushResponse {

    private List<LunarCrushData> data;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class LunarCrushData {

    private String symbol;
    private double social_score;
    private double social_impact_score;
    private double average_sentiment;

    public double getSocialScore() {
      return (social_score + social_impact_score + average_sentiment) / 3;
    }
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class CryptoCompareResponse {

    private List<CryptoCompareData> data;
  }

  @Data
  @JsonIgnoreProperties(ignoreUnknown = true)
  static class CryptoCompareData {

    private int time;
    private int comments;
    private int positive_comments;
    private int negative_comments;

    public int getComments() {
      return comments;
    }

    public int getPositiveComments() {
      return positive_comments;
    }

    public int getNegativeComments() {
      return negative_comments;
    }
  }
}