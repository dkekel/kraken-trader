package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.dto.OrderResult;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

/**
 * The KrakenApiService class provides methods for evaluating trading conditions based on moving
 * averages and executing trades by placing limit orders through the Kraken API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("real-trading")
public class KrakenApiService implements TradingApiService {

  private static final double FALLBACK_FEE_RATE = 0.4;
  private static final int MAX_RETRIES = 5;
  private static final long RETRY_DELAY_MS = 1000;

  @Value("${kraken.api.key}")
  private String apiKey;
  @Value("${kraken.api.secret}")
  private String apiSecret;

  /**
   * Retrieves the current account balances from the Kraken API.
   *
   * @return a map where keys are asset names and values are their corresponding balances.
   * @throws Exception if the API call fails or the response cannot be parsed.
   */
  @Override
  public Map<String, Double> getAccountBalance() throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData = "nonce=" + nonce;

    // The path should be the URI path, not including the domain
    String path = "/0/private/Balance";
    String signature = getApiSignature(path, nonce, postData);

    // Send request to Kraken API
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.kraken.com" + path))
          .header("API-Key", apiKey)
          .header("API-Sign", signature)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(postData))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject responseBody = new JSONObject(response.body());

      if (responseBody.has("error") && !responseBody.getJSONArray("error").isEmpty()) {
        throw new RuntimeException(
            "Kraken API returned an error: " + responseBody.getJSONArray("error"));
      }

      JSONObject result = responseBody.getJSONObject("result");
      Map<String, Double> balances = new HashMap<>();
      for (String asset : result.keySet()) {
        double balance = result.getDouble(asset);
        if (balance > 0) { // Only include assets with non-zero balance
          balances.put(asset, balance);
        }
      }
      return balances;
    }
  }

  @Override
  public Double getAssetBalance(String asset) throws Exception {
    Map<String, Double> balances = getAccountBalance();
    var assetKey = balances.keySet().stream().filter(balanceAsset -> balanceAsset.contains(asset))
        .findFirst();
    if (assetKey.isPresent()) {
      return balances.get(assetKey.get());
    }
    throw new RuntimeException("No balance for asset: " + asset);
  }

  /**
   * Creates an API signature for private Kraken API calls.
   *
   * @param path     The URI path
   * @param nonce    The nonce value
   * @param postData The POST data
   * @return The signature as a Base64 encoded string
   * @throws Exception if signature generation fails
   */
  public String getApiSignature(String path, String nonce, String postData) throws Exception {
    // Message signature using HMAC-SHA512 of (URI path + SHA256(nonce + POST data)) and base64 decoded secret API key
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    md.update((nonce + postData).getBytes());

    Mac mac = Mac.getInstance("HmacSHA512");
    byte[] secretBytes = Base64.getDecoder().decode(apiSecret);
    mac.init(new SecretKeySpec(secretBytes, "HmacSHA512"));

    mac.update(path.getBytes());
    mac.update(md.digest());

    return Base64.getEncoder().encodeToString(mac.doFinal());
  }

  /**
   * Places a market buy order
   * 
   * @param coin the trading pair (e.g., "XBTUSD")
   * @param amount the amount to buy
   * @return OrderResult containing order details including fees
   * @throws Exception if the API call fails
   */
  @Override
  public OrderResult placeMarketBuyOrder(String coin, double amount) throws Exception {
    return placeMarketOrder(coin, amount, "buy");
  }

  /**
   * Places a market sell order
   * 
   * @param coin the trading pair (e.g., "XBTUSD")
   * @param amount the amount to sell
   * @return OrderResult containing order details including fees
   * @throws Exception if the API call fails
   */
  @Override
  public OrderResult placeMarketSellOrder(String coin, double amount) throws Exception {
    return placeMarketOrder(coin, amount, "sell");
  }

  /**
   * Places a market order (buy or sell)
   * 
   * @param coin the trading pair (e.g., "XBTUSD")
   * @param amount the amount to trade
   * @param type the order type ("buy" or "sell")
   * @return OrderResult containing order details including fees
   * @throws Exception if the API call fails
   */
  private OrderResult placeMarketOrder(String coin, double amount, String type) throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData =
        "nonce=" + nonce + "&ordertype=market&pair=" + coin + "&type=" + type + "&volume=" + amount;

    var path = "/0/private/AddOrder";
    var signature = getApiSignature(path, nonce, postData);

    // Send request
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.kraken.com" + path))
          .header("API-Key", apiKey)
          .header("API-Sign", signature)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(postData))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      log.info("Order Response: " + response.body());

      // Parse the response to get order details
      JSONObject responseJson = new JSONObject(response.body());

      if (responseJson.has("error") && !responseJson.getJSONArray("error").isEmpty()) {
        throw new RuntimeException("Kraken API error: "
            + responseJson.getJSONArray("error").toString());
      }

      // Get the order ID from the result
      JSONObject result = responseJson.getJSONObject("result");
      String orderId = result.getJSONArray("txid").getString(0);

      // Get order details including fees by querying the order status
      return getOrderDetails(orderId, coin, amount);
    }
  }

  /**
   * Gets the details of an order, including fees and executed price
   *
   * @param orderId the ID of the order
   * @return OrderResult containing order details
   * @throws Exception if the API call fails
   */
  private OrderResult getOrderDetails(String orderId, String coin, double amount) throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData = "nonce=" + nonce + "&txid=" + orderId;

    var path = "/0/private/QueryOrders";
    var signature = getApiSignature(path, nonce, postData);

    for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
      try (HttpClient client = HttpClient.newHttpClient()) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.kraken.com" + path))
            .header("API-Key", apiKey)
            .header("API-Sign", signature)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(postData))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject responseJson = new JSONObject(response.body());

        if (responseJson.has("error") && !responseJson.getJSONArray("error").isEmpty()) {
          throw new RuntimeException("Kraken API error: "
              + responseJson.getJSONArray("error").toString());
        }

        JSONObject result = responseJson.getJSONObject("result");

        // Check if order details are available
        if (result.has(orderId)) {
          JSONObject order = result.getJSONObject(orderId);

          double fee = order.getDouble("fee");
          double price = order.getDouble("price");
          double volume = order.getDouble("vol_exec");

          return new OrderResult(orderId, fee, price, volume);
        } else {
          // Order not yet available, log and retry
          log.info("Order details not yet available for order {}. Retry attempt {}/{}.",
              orderId, attempt + 1, MAX_RETRIES);
          log.info("Order validation response: {}", responseJson);

          if (attempt < MAX_RETRIES - 1) {
            // Wait before retrying
            Thread.sleep(RETRY_DELAY_MS);
            // Generate new nonce and signature for the next attempt
            nonce = String.valueOf(System.currentTimeMillis());
            postData = "nonce=" + nonce + "&txid=" + orderId;
            signature = getApiSignature(path, nonce, postData);
          }
        }
      } catch (Exception e) {
        log.warn("Error retrieving order details (attempt {}/{}): {}",
            attempt + 1, MAX_RETRIES, e.getMessage());

        if (attempt < MAX_RETRIES - 1) {
          Thread.sleep(RETRY_DELAY_MS);
        }
      }
    }

    // If we reached here, we couldn't get the order details after all retries
    // Return a basic OrderResult with just the orderId to prevent repeated orders
    log.warn("Could not retrieve full order details after {} attempts. " +
            "Returning basic order information to prevent reordering.",
        MAX_RETRIES);

    // Fall back to estimated values
    double estimatedPrice = getCurrentPrice(coin);
    return new OrderResult(orderId, FALLBACK_FEE_RATE, estimatedPrice, amount);
  }

  /**
   * Gets the current market price for a coin pair
   *
   * @param pair The trading pair (e.g., "XBTUSD")
   * @return Current market price
   * @throws Exception if the API call fails
   */
  private double getCurrentPrice(String pair) throws Exception {
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.kraken.com/0/public/Ticker?pair=" + pair))
          .GET()
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject responseJson = new JSONObject(response.body());

      if (responseJson.has("error") && !responseJson.getJSONArray("error").isEmpty()) {
        log.error("Kraken API error: {}", responseJson.getJSONArray("error").toString());
        return 0.0d;
      }

      JSONObject result = responseJson.getJSONObject("result");
      String firstKey = result.keys().next();
      JSONObject pairData = result.getJSONObject(firstKey);

      // Return the last trade price
      JSONArray c = pairData.getJSONArray("c");
      return Double.parseDouble(c.getString(0));
    } catch (Exception e) {
      log.error("Failed to get current price for pair {}", pair, e);
      return 0.0d;
    }
  }

  /**
   * Retrieves the top N coins by volume from the Kraken public API.
   *
   * @param limit the number of coins to return
   * @return List of coin symbols sorted by volume in descending order.
   */
  public List<String> getTopCoinsByVolume(int limit) {
    String url = "https://api.kraken.com/0/public/AssetPairs";

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject json = new JSONObject(response.body());
      JSONObject result = json.getJSONObject("result");

      // Extract asset pairs and sort by volume (descending)
      List<String> pairs = new ArrayList<>();
      result.keys().forEachRemaining(pairs::add);

      var coinPairs = pairs.stream()
          .map(result::getJSONObject)
          .filter(pair -> pair.getString("quote").equals("ZUSD"))
          .filter(pair -> pair.has("wsname"))
          .map(pair -> pair.getString("wsname"))
          .toList();

      var volumes = getCoinVolumes(coinPairs);

      return volumes.entrySet().stream()
          .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
          .map(Map.Entry::getKey)
          .limit(limit)
          .toList();
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to fetch top coins by volume: " + e.getMessage(), e);
    }
  }


  /**
   * Fetches the volumes of the specified coin pairs from Kraken's Trades API.
   *
   * @param coinPairs a list of coin pairs
   * @return a map where keys are coin pairs and values are the corresponding volumes
   */
  public Map<String, Double> getCoinVolumes(List<String> coinPairs) {
    String apiUrlTemplate = "https://api.kraken.com/0/public/Trades?pair=%s&since=%d";
    Map<String, Double> volumes = new HashMap<>();

    for (String pair : coinPairs) {
      long twelveHoursAgo =
          System.currentTimeMillis() / 1000 - 12 * 3600; // Current time minus 12 hours in seconds
      String url = apiUrlTemplate.formatted(pair, twelveHoursAgo);

      try (HttpClient client = HttpClient.newHttpClient()) {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        if (json.has("result")) {
          JSONObject result = json.getJSONObject("result");
          JSONArray trades = result.getJSONArray(pair);

          // Use the highest trade volume for the pair
          double volume = 0;
          for (int i = 0; i < trades.length(); i++) {
            JSONArray trade = trades.getJSONArray(i);
            double tradeVolume = trade.getDouble(1);  // Volume is the second element in the array
            if (tradeVolume > volume) {
              volume = tradeVolume;
            }
          }

          volumes.put(pair, volume);
        }
      } catch (IOException | InterruptedException e) {
        throw new RuntimeException("Failed to fetch volume for pair: " + pair, e);
      }
    }

    return volumes;
  }

  /**
   * Retrieves the current fee information for the specified pair.
   *
   * @param pair Trading pair (e.g., "XBTUSD")
   * @return A map containing fee information including maker and taker fees
   */
  @Override
  @Cacheable(value = "feeInfo", key = "#pair", cacheManager = "tradingCacheManager")
  public double getCoinTradingFee(String pair) {
    double takerFeeRate = FALLBACK_FEE_RATE;

    try {
      // Get current fees from Kraken
      var feeInfo = getTradingFees(pair);

      // For market orders, we use the taker fee
      takerFeeRate = feeInfo.get("takerFee");
    } catch (Exception e) {
      // If fee retrieval fails, fall back to a conservative estimate
      log.warn("Failed to retrieve fees from Kraken API: {}. Falling back to default fee {}",
          e.getMessage(), FALLBACK_FEE_RATE, e);
    }

    return takerFeeRate;
  }

  private Map<String, Double> getTradingFees(String pair) throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData = "nonce=" + nonce + "&pair=" + pair + "&fee-info=true";

    String path = "/0/private/TradeVolume";
    String signature = getApiSignature(path, nonce, postData);

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.kraken.com" + path))
          .header("API-Key", apiKey)
          .header("API-Sign", signature)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(postData))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject responseJson = new JSONObject(response.body());

      if (responseJson.has("error") && !responseJson.getJSONArray("error").isEmpty()) {
        throw new RuntimeException("Kraken API error: " + responseJson.getJSONArray("error").toString());
      }

      Map<String, Double> feeInfo = new HashMap<>();
      JSONObject result = responseJson.getJSONObject("result");

      // Extract current fee tier
      if (result.has("fees")) {
        JSONObject feesObj = result.getJSONObject("fees");
        if (!feesObj.isEmpty()) {
          // Get the first key from the fees object
          String firstPairKey = feesObj.keys().next();
          JSONObject pairFees = feesObj.getJSONObject(firstPairKey);

          feeInfo.put("makerFee", pairFees.getDouble("fee"));
          feeInfo.put("takerFee", pairFees.getDouble("fee"));

          log.debug("Retrieved fee info for {} (using key {}): maker={}, taker={}",
              pair, firstPairKey, feeInfo.get("makerFee"), feeInfo.get("takerFee"));
        }
      }

      // Extract current 30-day volume if available
      if (result.has("volume")) {
        feeInfo.put("volume", result.getDouble("volume"));
      }

      return feeInfo;
    }
  }
}
