package ch.kekelidze.krakentrader.api.service;

import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

/**
 * The KrakenApiService class provides methods for evaluating trading conditions based on moving
 * averages and executing trades by placing limit orders through the Kraken API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenApiService {

  // Constant values for moving average periods and trading amount
  private static final int MA_9 = 9;
  private static final int MA_50 = 50;
  private static final int MA_100 = 100;
  private static final int MA_200 = 200;
  private static final double TRADING_AMOUNT = 15.0;

  @Value("${kraken.api.key}")
  private String apiKey;
  @Value("${kraken.api.secret}")
  private String apiSecret;

  private final ResponseConverterUtils responseConverterUtils;

//  private void placeLimitOrder(String coin, double amount) {
//    try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
//      String apiEndpoint = "https://api.kraken.com/0/private/AddOrder";
//
//      // Construct the request payload
//      String body = "nonce=" + System.currentTimeMillis() +
//          "&ordertype=limit" +
//          "&type=buy" +
//          "&pair=" + coin +
//          "&volume=" + amount;
//
//      HttpPost post = new HttpPost(apiEndpoint);
//      post.setEntity(new StringEntity(body, ContentType.APPLICATION_FORM_URLENCODED));
//
//      // Set the required headers (example placeholders, replace as needed)
//      post.setHeader("API-Key", "<Your-API-Key>");
//      post.setHeader("API-Sign", "<Your-API-Signature>");
//
//      try (CloseableHttpResponse response = httpClient.execute(post)) {
//        if (response.getStatusLine().getStatusCode() == 200) {
//          String jsonResponse = EntityUtils.toString(response.getEntity());
//          ObjectMapper mapper = new ObjectMapper();
//          JsonNode rootNode = mapper.readTree(jsonResponse);
//
//          if (!rootNode.path("error").isEmpty()) {
//            throw new RuntimeException(
//                "Kraken API returned error: " + rootNode.path("error").toString());
//          }
//
//          System.out.println("Limit order placed successfully. Response: " + jsonResponse);
//        } else {
//          throw new RuntimeException(
//              "API call failed with status code: " + response.getStatusLine().getStatusCode());
//        }
//      }
//    } catch (Exception e) {
//      throw new RuntimeException("Failed to place limit order: " + e.getMessage(), e);
//    }
//  }

  public void placeMarketOrder(String coin, double amount) throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData =
        "nonce=" + nonce + "&ordertype=market&pair=" + coin + "&type=buy&volume=" + amount;

    var signature = getApiSignature(nonce, postData);

    // Send request
    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create("https://api.kraken.com/0/private/AddOrder"))
          .header("API-Key", apiKey)
          .header("API-Sign", signature)
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(postData))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      log.info("Order Response: " + response.body());
    }
  }

  private String getApiSignature(String nonce, String postData) throws Exception {
    // Sign the request
    Mac sha512 = Mac.getInstance("HmacSHA512");
    byte[] secretBytes = Base64.getDecoder().decode(apiSecret);
    sha512.init(new SecretKeySpec(secretBytes, "HmacSHA512"));
    byte[] hash = sha512.doFinal((nonce + postData).getBytes());
    return Base64.getEncoder().encodeToString(hash);
  }

  /**
   * Kraken’s OHLC Format: Each candle is an array [time, open, high, low, close, vwap, volume,
   * count].
   *
   * @param coin   coin to get data for
   * @param period period duration, e.g. 60 for 1-hour candles
   * @return list of closing prices per period
   */
  public List<Bar> queryHistoricalData(String coin, int period) {
    String url =
        "https://api.kraken.com/0/public/OHLC?pair=" + coin + "&interval=" + period;

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject json = new JSONObject(response.body());
      JSONObject result = json.getJSONObject("result");
      JSONArray ohlcData = result.keySet().stream().findFirst().map(result::getJSONArray)
          .orElse(new JSONArray());

      // Extract closing prices (index 4 in Kraken's OHLC array)
      var dataBars = new ArrayList<Bar>();
      for (int i = 0; i < ohlcData.length(); i++) {
        var ohlcCandle = ohlcData.getJSONArray(i);
        var bar = responseConverterUtils.getPriceBar(ohlcCandle, period);
        dataBars.add(bar);
      }

      return dataBars;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to fetch and parse historical data: " + e.getMessage(), e);
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
}
