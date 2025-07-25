package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.dto.OrderResult;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

/**
 * Paper trading implementation of the TradingApiService interface.
 * This service simulates trading operations without making actual API calls to Kraken.
 * It uses the real KrakenApiService for historical data to maintain realistic price data.
 */
@Slf4j
@Service
@Profile("paper-trading")
public class PaperTradeKrakenApiService implements TradingApiService {

  private static final double DEFAULT_FEE_RATE = 0.26; // Default fee rate for paper trading (0.26%)
  
  private final HistoricalDataService historicalDataService;
  private final Map<String, Double> paperBalances = new ConcurrentHashMap<>();
  
  @Autowired
  public PaperTradeKrakenApiService(HistoricalDataService historicalDataService,
      @Value("${paper.trading.initial.balance:10000}") double initialBalance) {
    this.historicalDataService = historicalDataService;
    // Initialize with default USD balance
    paperBalances.put("USD", initialBalance);
    log.info("Paper trading mode initialized with ${} USD", initialBalance);
  }
  
  @Override
  public OrderResult placeMarketBuyOrder(String coin, double amount) throws Exception {
    log.info("PAPER TRADE: Placing market buy order for {} {}", amount, coin);
    
    // Get the current price from historical data
    double currentPrice = getCurrentPrice(coin);
    double fee = amount * currentPrice * (DEFAULT_FEE_RATE / 100);
    double totalCost = amount * currentPrice + fee;
    
    // Check if we have enough balance
    String baseCurrency = "USD"; // Assuming USD is the base currency
    double baseBalance = paperBalances.getOrDefault(baseCurrency, 0.0);
    
    if (baseBalance < totalCost) {
      throw new Exception("Insufficient funds for paper trade: " + baseBalance + " < " + totalCost);
    }
    
    // Update balances
    paperBalances.put(baseCurrency, baseBalance - totalCost);
    
    // Extract the asset code from the pair (e.g., "XBTUSD" -> "XBT")
    String assetCode = extractAssetCode(coin);
    double assetBalance = paperBalances.getOrDefault(assetCode, 0.0);
    paperBalances.put(assetCode, assetBalance + amount);
    
    log.info("PAPER TRADE: Bought {} {} at {} for a total of {} (including fee: {})",
        amount, coin, currentPrice, totalCost, fee);
    log.info("PAPER TRADE: New balances - {}: {}, {}: {}", 
        baseCurrency, paperBalances.get(baseCurrency), assetCode, paperBalances.get(assetCode));
    
    // Create and return order result
    return new OrderResult(UUID.randomUUID().toString(), fee, currentPrice, amount);
  }
  
  @Override
  public OrderResult placeMarketSellOrder(String coin, double amount) throws Exception {
    log.info("PAPER TRADE: Placing market sell order for {} {}", amount, coin);
    
    // Get the current price from historical data
    double currentPrice = getCurrentPrice(coin);
    double fee = amount * currentPrice * (DEFAULT_FEE_RATE / 100);
    double totalProceeds = amount * currentPrice - fee;
    
    // Extract the asset code from the pair (e.g., "XBTUSD" -> "XBT")
    String assetCode = extractAssetCode(coin);
    double assetBalance = paperBalances.getOrDefault(assetCode, 0.0);
    
    // Check if we have enough of the asset to sell
    if (assetBalance < amount) {
      throw new Exception("Insufficient asset for paper trade: " + assetBalance + " < " + amount);
    }
    
    // Update balances
    paperBalances.put(assetCode, assetBalance - amount);
    
    String baseCurrency = "USD"; // Assuming USD is the base currency
    double baseBalance = paperBalances.getOrDefault(baseCurrency, 0.0);
    paperBalances.put(baseCurrency, baseBalance + totalProceeds);
    
    log.info("PAPER TRADE: Sold {} {} at {} for a total of {} (including fee: {})",
        amount, coin, currentPrice, totalProceeds, fee);
    log.info("PAPER TRADE: New balances - {}: {}, {}: {}", 
        baseCurrency, paperBalances.get(baseCurrency), assetCode, paperBalances.get(assetCode));
    
    // Create and return order result
    return new OrderResult(UUID.randomUUID().toString(), fee, currentPrice, amount);
  }
  
  @Override
  public double getCoinTradingFee(String pair) {
    // Return a fixed fee rate for paper trading
    return DEFAULT_FEE_RATE;
  }
  
  @Override
  public Map<String, Double> getAccountBalance() {
    // Return a copy of the paper balances
    return new HashMap<>(paperBalances);
  }
  
  @Override
  public Double getAssetBalance(String asset) {
    // Find the asset in our paper balances
    for (Map.Entry<String, Double> entry : paperBalances.entrySet()) {
      if (entry.getKey().contains(asset)) {
        return entry.getValue();
      }
    }
    return 0.0; // Return 0 if not found
  }
  
  /**
   * Gets the current price for a coin pair from historical data
   * 
   * @param coin The coin pair (e.g., "XBTUSD")
   * @return The current price
   * @throws Exception If price data cannot be retrieved
   */
  private double getCurrentPrice(String coin) throws Exception {
    List<String> coinList = List.of(coin);
    Map<String, List<Bar>> historicalData = historicalDataService.queryHistoricalData(coinList, 1);
    
    if (!historicalData.containsKey(coin) || historicalData.get(coin).isEmpty()) {
      throw new Exception("Could not get current price for " + coin);
    }
    
    // Get the latest price from the historical data
    Bar latestBar = historicalData.get(coin).getLast();
    return latestBar.getClosePrice().doubleValue();
  }
  
  /**
   * Extracts the asset code from a trading pair
   * 
   * @param pair The trading pair (e.g., "XBTUSD")
   * @return The asset code (e.g., "XBT")
   */
  private String extractAssetCode(String pair) {
    // Simple implementation - assumes format like "XBTUSD" where the first 3 chars are the asset
    // In a real implementation, this would need to be more sophisticated
    if (pair.length() <= 3) {
      return pair;
    }
    return pair.substring(0, pair.length() - 3);
  }

  @Override
  public String getApiSignature(String path, String nonce, String postData) {
    return "";
  }
  
  @Override
  public double getMinimumOrderVolume(String pair) {
    // For paper trading, use reasonable default minimum volumes based on common assets
    if (pair.startsWith("XBT") || pair.startsWith("BTC")) {
      return 0.001; // 0.001 BTC minimum
    } else if (pair.startsWith("ETH")) {
      return 0.01; // 0.01 ETH minimum
    } else if (pair.startsWith("XDG")) {
      return 100.0; // 100 DOGE minimum
    } else if (pair.startsWith("SHIB")) {
      return 500000.0; // 500,000 SHIB minimum
    } else {
      // Default minimum for other assets
      return 0.01;
    }
  }
}