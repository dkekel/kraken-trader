package ch.kekelidze.krakentrader.api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * The KrakenApiService class provides methods for evaluating trading conditions based on moving
 * averages and executing trades by placing limit orders through the Kraken API.
 */
@Slf4j
@Service
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
  public List<Double> queryHistoricalData(String coin, int period) {
    String url = "https://api.kraken.com/0/public/OHLC?pair=" + coin + "&interval=" + period;

    try (HttpClient client = HttpClient.newHttpClient()) {
      HttpRequest request = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .build();

      HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
      JSONObject json = new JSONObject(response.body());
      JSONObject result = json.getJSONObject("result");
      JSONArray ohlcData = result.getJSONArray(coin);

      // Extract closing prices (index 4 in Kraken's OHLC array)
      double[] closes = new double[ohlcData.length()];
      for (int i = 0; i < ohlcData.length(); i++) {
        closes[i] = ohlcData.getJSONArray(i).getDouble(4);
      }

      log.info("Latest 9 closes:");
      List<Double> historicalData = new ArrayList<>();
      for (double close : closes) {
        log.info("{}", close);
        historicalData.add(close);
      }
      return historicalData;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to fetch and parse historical data: " + e.getMessage(), e);
    }
  }
}
