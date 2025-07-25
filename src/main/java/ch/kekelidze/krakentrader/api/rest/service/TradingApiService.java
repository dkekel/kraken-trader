package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.dto.OrderResult;
import java.util.Map;

/**
 * Interface for trading API services that can be used for both real trading and paper trading.
 * Extends HistoricalDataService to include historical data querying capabilities.
 */
public interface TradingApiService {

  /**
   * Places a market buy order
   * 
   * @param coin the trading pair (e.g., "XBTUSD")
   * @param amount the amount to buy
   * @return OrderResult containing order details including fees
   * @throws Exception if the operation fails
   */
  OrderResult placeMarketBuyOrder(String coin, double amount) throws Exception;

  /**
   * Places a market sell order
   * 
   * @param coin the trading pair (e.g., "XBTUSD")
   * @param amount the amount to sell
   * @return OrderResult containing order details including fees
   * @throws Exception if the operation fails
   */
  OrderResult placeMarketSellOrder(String coin, double amount) throws Exception;

  /**
   * Retrieves the trading fee for a specific coin pair
   * 
   * @param pair Trading pair (e.g., "XBTUSD")
   * @return The trading fee as a percentage
   */
  double getCoinTradingFee(String pair);

  /**
   * Retrieves the current account balances
   *
   * @return a map where keys are asset names and values are their corresponding balances.
   * @throws Exception if the operation fails
   */
  Map<String, Double> getAccountBalance() throws Exception;

  /**
   * Gets the balance of a specific asset
   *
   * @param asset The asset name
   * @return The balance of the asset
   * @throws Exception if the operation fails
   */
  Double getAssetBalance(String asset) throws Exception;

  /**
   * Creates an API signature for private Kraken API calls.
   *
   * @param path     The URI path
   * @param nonce    The nonce value
   * @param postData The POST data
   * @return The signature as a Base64 encoded string
   * @throws Exception if signature generation fails
   */
  String getApiSignature(String path, String nonce, String postData) throws Exception;
  
  /**
   * Gets the minimum order volume for a specific trading pair.
   * This is the minimum amount that can be traded on Kraken for this pair.
   *
   * @param pair Trading pair (e.g., "XBT/USD")
   * @return The minimum order volume for the pair
   */
  double getMinimumOrderVolume(String pair);
}