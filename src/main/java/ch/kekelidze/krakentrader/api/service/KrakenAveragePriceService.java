package ch.kekelidze.krakentrader.api.service;

import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;

/**
 * Service to calculate average purchase prices for assets on Kraken
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenAveragePriceService {

  @Value("${kraken.api.key}")
  private String apiKey;

  @Value("${kraken.api.secret}")
  private String apiSecret;

  private final KrakenApiService krakenApiService;

  // Asset name mappings (Kraken uses X prefix for many crypto assets)
  private static final Map<String, String> ASSET_MAPPINGS = Map.ofEntries(
      Map.entry("XETH", "ETH"),
      Map.entry("XXRP", "XRP"),
      Map.entry("HONEY", "HONEY"),
      Map.entry("ETH2.S", "ETH2.S"),
      Map.entry("ETH.F", "ETH.F"),
      Map.entry("XXDG", "DOGE"),
      Map.entry("FLR", "FLR"),
      Map.entry("FLR.S", "FLR.S"),
      Map.entry("PEPE", "PEPE"),
      Map.entry("SGB", "SGB"),
      Map.entry("ETH2", "ETH2")
  );

  /**
   * Transaction record for processing trade history
   */
  private static class Transaction {

    Instant time;
    String type; // buy or sell
    String asset;
    double quantity;
    double price;
    double fee;

    Transaction(Instant time, String type, String asset, double quantity, double price,
        double fee) {
      this.time = time;
      this.type = type;
      this.asset = asset;
      this.quantity = quantity;
      this.price = price;
      this.fee = fee;
    }
  }

  /**
   * Lot record for FIFO calculations
   */
  private static class Lot {

    Instant purchaseTime;
    double quantity;
    double price;
    double fee;

    Lot(Instant purchaseTime, double quantity, double price, double fee) {
      this.purchaseTime = purchaseTime;
      this.quantity = quantity;
      this.price = price;
      this.fee = fee;
    }
  }

  /**
   * Get the average purchase price for all assets currently held. Uses FIFO accounting method for
   * calculating the cost basis after sells.
   *
   * @return Map of asset symbols to their average purchase prices
   * @throws Exception if API call fails
   */
  public Map<String, Double> getAveragePurchasePrices() throws Exception {
    // Get the complete list of transactions
    List<Transaction> transactions = getAllTransactions();

    // Sort transactions by time (oldest first)
    transactions.sort(Comparator.comparing(t -> t.time));

    // Maps to hold our lots and results
    Map<String, Queue<Lot>> assetLots = new HashMap<>();
    Map<String, Double> currentAveragePrices = new HashMap<>();
    Map<String, Double> currentQuantities = new HashMap<>();

    // Process each transaction in chronological order
    for (Transaction tx : transactions) {
      String asset = standardizeAssetName(tx.asset);

      // Initialize collections if this is the first time seeing this asset
      assetLots.putIfAbsent(asset, new LinkedList<>());
      currentAveragePrices.putIfAbsent(asset, 0.0);
      currentQuantities.putIfAbsent(asset, 0.0);

      if ("buy".equals(tx.type)) {
        // Buy transaction
        double currentQuantity = currentQuantities.get(asset);
        double currentAvgPrice = currentAveragePrices.get(asset);

        // Calculate new average price
        double newTotalQuantity = currentQuantity + tx.quantity;
        double newTotalCost =
            (currentQuantity * currentAvgPrice) + (tx.quantity * tx.price) + tx.fee;
        double newAvgPrice = newTotalCost / newTotalQuantity;

        // Update current state
        currentQuantities.put(asset, newTotalQuantity);
        currentAveragePrices.put(asset, newAvgPrice);

        // Add a new lot
        assetLots.get(asset).add(new Lot(tx.time, tx.quantity, tx.price, tx.fee));

        log.debug("Buy {} {} at {}: New avg price={}, Total quantity={}",
            tx.quantity, asset, tx.price, newAvgPrice, newTotalQuantity);
      } else if ("sell".equals(tx.type)) {
        // Sell transaction - use FIFO to remove the appropriate lots
        double remainingToSell = tx.quantity;
        Queue<Lot> lots = assetLots.get(asset);

        while (remainingToSell > 0 && !lots.isEmpty()) {
          Lot oldestLot = lots.peek();

          if (oldestLot.quantity <= remainingToSell) {
            // Use the entire lot
            remainingToSell -= oldestLot.quantity;
            lots.poll(); // Remove the lot
          } else {
            // Use part of the lot
            oldestLot.quantity -= remainingToSell;
            remainingToSell = 0;
          }
        }

        // Recalculate average price and quantity
        double totalQuantity = 0;
        double totalCost = 0;

        for (Lot lot : lots) {
          totalQuantity += lot.quantity;
          totalCost += lot.quantity * lot.price + (lot.fee * (lot.quantity / tx.quantity));
        }

        double newAvgPrice = totalQuantity > 0 ? totalCost / totalQuantity : 0;

        currentQuantities.put(asset, totalQuantity);
        currentAveragePrices.put(asset, newAvgPrice);

        log.debug("Sell {} {} at {}: New avg price={}, Remaining quantity={}",
            tx.quantity, asset, tx.price, newAvgPrice, totalQuantity);
      }
    }

    // Filter out assets with zero quantity
    Map<String, Double> result = new HashMap<>();
    for (String asset : currentAveragePrices.keySet()) {
      if (currentQuantities.get(asset) > 0) {
        result.put(asset, currentAveragePrices.get(asset));
      }
    }

    return result;
  }

  /**
   * Get all transactions from the Kraken API, handling pagination
   *
   * @return List of all transactions
   * @throws Exception if API call fails
   */
  private List<Transaction> getAllTransactions() throws Exception {
    List<Transaction> allTransactions = new ArrayList<>();
    boolean hasMore = true;
    String offset = "0";

    while (hasMore) {
      JSONObject response = getTradesHistory(offset);
      JSONObject result = response.getJSONObject("result");
      JSONObject trades = result.getJSONObject("trades");

      // Process trades in this batch
      for (String tradeId : trades.keySet()) {
        JSONObject trade = trades.getJSONObject(tradeId);

        String pair = trade.getString("pair");
        String asset = extractAssetFromPair(pair);
        String type = trade.getString("type");
        double volume = trade.getDouble("vol");
        double price = trade.getDouble("price");
        double fee = trade.getDouble("fee");
        long timeUnix = trade.getLong("time");
        Instant time = Instant.ofEpochSecond((long) timeUnix);

        allTransactions.add(new Transaction(time, type, asset, volume, price, fee));
      }

      // Check if there are more trades
      hasMore = result.has("count") && trades.length() < result.getInt("count");

      // Update offset for next batch if needed
      if (hasMore && result.has("last")) {
        offset = result.getString("last");
      } else {
        hasMore = false;
      }
    }

    return allTransactions;
  }

  /**
   * Extract base asset from trading pair Examples: - XXBTZUSD -> XXBT (Bitcoin) - XETHZUSD -> XETH
   * (Ethereum)
   *
   * @param pair The trading pair from Kraken API
   * @return The base asset code
   */
  private String extractAssetFromPair(String pair) {
    // Most crypto pairs on Kraken are 8 characters
    // First 4 are the base asset, last 4 are the quote asset
    if (pair.length() == 8) {
      return pair.substring(0, 4);
    }

    // For other formats, try to determine based on common quote currencies
    if (pair.endsWith("USD") || pair.endsWith("EUR") || pair.endsWith("ZUSD") || pair.endsWith(
        "ZEUR")) {
      return pair.substring(0, pair.length() - 3);
    }

    // Default case, return as is
    return pair;
  }

  /**
   * Convert Kraken's asset codes to standard symbols
   *
   * @param krakenAsset The asset code from Kraken API
   * @return Standardized asset symbol
   */
  private String standardizeAssetName(String krakenAsset) {
    return ASSET_MAPPINGS.getOrDefault(krakenAsset, krakenAsset);
  }

  /**
   * Call Kraken's TradesHistory API
   *
   * @param offset Pagination offset
   * @return JSONObject containing the API response
   * @throws Exception if API call fails
   */
  private JSONObject getTradesHistory(String offset) throws Exception {
    String nonce = String.valueOf(System.currentTimeMillis());
    String postData = "nonce=" + nonce + "&ofs=" + offset;

    var path = "/0/private/TradesHistory";
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://api.kraken.com/" + path))
        .header("API-Key", apiKey)
        .header("API-Sign", krakenApiService.getApiSignature(path, nonce, postData))
        .header("Content-Type", "application/x-www-form-urlencoded")
        .POST(HttpRequest.BodyPublishers.ofString(postData))
        .build();

    try (HttpClient httpClient = HttpClient.newHttpClient()) {
      HttpResponse<String> response = httpClient.send(request,
          HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200) {
        throw new RuntimeException("API call failed with status code: " + response.statusCode());
      }

      String jsonResponse = response.body();
      JSONObject result = new JSONObject(jsonResponse);

      if (result.has("error") && !result.getJSONArray("error").isEmpty()) {
        throw new RuntimeException("Kraken API error: " + result.getJSONArray("error").toString());
      }

      return result;
    }
  }
}