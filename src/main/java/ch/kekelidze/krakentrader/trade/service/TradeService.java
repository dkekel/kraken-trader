package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.api.dto.OrderResult;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.TradeOperationType;
import ch.kekelidze.krakentrader.trade.TradeState;
import ch.kekelidze.krakentrader.trade.util.TradingCircuitBreaker;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class TradeService {

  private final Map<String, Object> coinPairLocks = new ConcurrentHashMap<>();
  private final Map<String, Long> lastTradeTimestamps = new ConcurrentHashMap<>();
  private final Map<String, Long> lastUsdResyncTimestamps = new ConcurrentHashMap<>();

  @Value("${trading.cooldown.minutes:15}")
  private int tradeCooldownMinutes;
  
  @Value("${trading.resync.minutes:60}")
  private int usdResyncIntervalMinutes;

  private final AtrAnalyser atrAnalyser;
  private final Portfolio portfolio;
  private final TradeStatePersistenceService tradeStatePersistenceService;
  private final TradingApiService tradingApiService;
  private final TradingCircuitBreaker circuitBreaker;
  @Getter
  @Setter
  private Strategy strategy;

  public TradeService(AtrAnalyser atrAnalyser, Portfolio portfolio,
      TradeStatePersistenceService tradeStatePersistenceService,
      TradingApiService tradingApiService,
      TradingCircuitBreaker circuitBreaker) {
    this.atrAnalyser = atrAnalyser;
    this.portfolio = portfolio;
    this.tradeStatePersistenceService = tradeStatePersistenceService;
    this.tradingApiService = tradingApiService;
    this.circuitBreaker = circuitBreaker;
  }

  @PostConstruct
  public void logConfiguration() {
    log.info("Trade cooldown configured to: {} minutes", tradeCooldownMinutes);
    log.info("USD balance resync interval configured to: {} minutes", usdResyncIntervalMinutes);
    log.info("Circuit breaker protection enabled");
  }
  
  /**
   * Checks if USD balance resync is needed for the given coin pair based on the configured interval.
   * 
   * @param coinPair The coin pair to check
   * @return true if USD balance resync is needed, false otherwise
   */
  private boolean isUsdResyncNeeded(String coinPair) {
    long currentTime = System.currentTimeMillis();
    Long lastResyncTime = lastUsdResyncTimestamps.get(coinPair);
    
    if (lastResyncTime == null) {
      // First time, resync needed
      return true;
    }
    
    long resyncIntervalMs = TimeUnit.MINUTES.toMillis(usdResyncIntervalMinutes);
    long timeSinceLastResync = currentTime - lastResyncTime;
    boolean resyncNeeded = timeSinceLastResync >= resyncIntervalMs;
    
    if (resyncNeeded) {
      log.debug("USD balance resync needed for {}: {} minutes since last resync", 
          coinPair, TimeUnit.MILLISECONDS.toMinutes(timeSinceLastResync));
    }
    
    return resyncNeeded;
  }
  
  /**
   * Records the timestamp of a successful USD balance resync.
   * 
   * @param coinPair The coin pair that was resynced
   */
  private void recordUsdResyncTimestamp(String coinPair) {
    lastUsdResyncTimestamps.put(coinPair, System.currentTimeMillis());
    log.debug("USD balance resync timestamp recorded for {}", coinPair);
  }
  
  /**
   * Counts the number of coins that are not in trade and are actively traded.
   * This uses the trade states from the portfolio to determine which coins are eligible for trading.
   *
   * @return The number of coins not in trade and actively traded
   */
  private int countCoinsNotInTrade() {
    int count = 0;
    var tradeStates = portfolio.getTradeStates().values();
    for (TradeState state : tradeStates) {
      if (!state.isInTrade() && state.isActivelyTraded()) {
        count++;
      }
    }
    return count;
  }

  public void executeStrategy(String coinPair, List<Bar> data) {
    Object lock = coinPairLocks.computeIfAbsent(coinPair, k -> new Object());
    synchronized (lock) {
      if (isUsdResyncNeeded(coinPair)) {
        log.info("Performing regular USD balance resync for {}", coinPair);
        try {
          resyncQuoteAssetBalance(coinPair);
          recordUsdResyncTimestamp(coinPair);
        } catch (Exception e) {
          log.error("Failed to resync USD balance for {}: {}", coinPair, e.getMessage());
          // Continue with strategy execution even if resync fails
        }
      }
      
      if (!circuitBreaker.canTrade(coinPair)) {
        var circuitState = circuitBreaker.getDetailedState(coinPair);
        log.warn(
            "Trading halted for {} - Circuit breaker {} | Consecutive losses: {} | Total loss: {}%",
            coinPair,
            circuitState.getState(),
            circuitState.getConsecutiveLosses(),
            String.format("%.2f", circuitState.getTotalLossPercent()));
        return;
      }

      if (!canTrade(coinPair)) {
        log.warn("Skipping strategy execution for {} due to trade cooldown", coinPair);
        return;
      }
      executeSelectedStrategy(coinPair, data, strategy.getStrategyParameters(coinPair), strategy);
    }
  }
  
  /**
   * Calculates the allocation for a coin pair based on a simplified approach.
   * 1. Calculate even allocation based on coins not in trade
   * 2. If even allocation is sufficient for minimum order volume, use it
   * 3. If not, check if available capital can cover the minimum for the current coin
   *
   * @param coinPair The coin pair
   * @param currentPrice The current price of the coin
   * @param totalCapital The total available capital
   * @return The allocated capital for this coin pair, or the minimum needed if even allocation is insufficient
   */
  private double calculateActualAllocation(String coinPair, double currentPrice, double totalCapital) {
    int coinsNotInTrade = countCoinsNotInTrade();
    double evenAllocation = totalCapital / coinsNotInTrade;

    try {
      double minVolume = tradingApiService.getMinimumOrderVolume(coinPair);
      // Calculate the minimum capital needed to meet the minimum order volume
      double minCapitalNeeded = minVolume * currentPrice;

      // If this is the only coin not in trade, use all available capital
      if (coinsNotInTrade <= 1) {
        log.info("This is the only coin not in trade, allocating all available capital: {} USD", totalCapital);

        // Check if we have enough capital to meet the minimum requirement
        if (totalCapital < minCapitalNeeded) {
          log.warn("Insufficient capital ({} USD) to meet minimum order volume for {} ({} USD needed)",
                  totalCapital, coinPair, minCapitalNeeded);
        }

        return totalCapital;
      }

      if (evenAllocation >= minCapitalNeeded) {
        log.info("Using even allocation of {} USD for {} (minimum needed: {} USD)",
                evenAllocation, coinPair, minCapitalNeeded);
        return evenAllocation;
      }

      // Otherwise, use the minimum capital needed
      log.info("Even allocation ({} USD) insufficient, using minimum needed ({} USD) for {}",
              evenAllocation, minCapitalNeeded, coinPair);
      return minCapitalNeeded;
    } catch (Exception e) {
      log.error("Failed to calculate allocation for {}: {}", coinPair, e.getMessage(), e);
      return evenAllocation;
    }
  }

  private void executeSelectedStrategy(String coinPair, List<Bar> data, StrategyParameters params,
      Strategy strategy) {
    var tradeState = portfolio.getOrCreateTradeState(coinPair);
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    double currentCapital = portfolio.getTotalCapital();

    if (currentCapital <= 0) {
      log.info("No capital available to trade {}", coinPair);
      return;
    }

    var evaluationContext = EvaluationContext.builder().symbol(coinPair).bars(data).build();
    var inTrade = tradeState.isInTrade();

    try {
      var buySignal = strategy.shouldBuy(evaluationContext, params);
      var sellSignal = false;
      if (inTrade) {
        sellSignal = strategy.shouldSell(evaluationContext, tradeState.getEntryPrice(), params);
      }

      if (buySignal && sellSignal) {
        log.warn(
            "Conflicting {} and {} signals detected for {} - skipping trade to avoid whipsaw",
            TradeOperationType.BUY, TradeOperationType.SELL, coinPair);
        return;
      }

      if (!inTrade && buySignal) {
        // Calculate position size based on allocated capital
        double allocatedCapital = calculateActualAllocation(coinPair, currentPrice, currentCapital);
        var positionSize = calculateAdaptivePositionSize(coinPair, data, currentPrice,
            allocatedCapital, params);

        // Place market buy order
        OrderResult orderResult = tradingApiService.placeMarketBuyOrder(coinPair, positionSize);

        // Set trade state with actual executed values
        tradeState.setInTrade(true);

        // Calculate entry price including fees
        var executedPrice = orderResult.executedPrice();
        double totalCost = executedPrice * orderResult.volume() + orderResult.fee();
        double entryPriceWithFees = totalCost / orderResult.volume();
        tradeState.setEntryPrice(entryPriceWithFees);

        // Use actual executed volume from the order
        tradeState.setPositionSize(orderResult.volume());
        tradeStatePersistenceService.saveTradeState(tradeState);

        // Update capital (deduct the total cost including fees)
        currentCapital = portfolio.addToTotalCapital(-totalCost);

        // Record trade timestamp for cooldown tracking
        recordTradeTimestamp(coinPair);

        var circuitState = circuitBreaker.getCircuitState(coinPair);
        log.info(
            "{} {} {} at: {} (including fees: {}) | Fee: {} | Circuit: {}",
            TradeOperationType.BUY,
            coinPair, 
            orderResult.volume(),
            executedPrice,
            entryPriceWithFees,
            orderResult.fee(),
            circuitState);

      } else if (inTrade && sellSignal) {
        // Place market sell order
        OrderResult orderResult = tradingApiService.placeMarketSellOrder(coinPair,
            tradeState.getPositionSize());

        // Calculate actual proceeds (after fees)
        var executedPrice =
            orderResult.executedPrice() == 0 ? currentPrice : orderResult.executedPrice();
        double totalProceeds = executedPrice * orderResult.volume() - orderResult.fee();

        // Calculate profit
        var entryPrice = tradeState.getEntryPrice();
        double entryValue = entryPrice * tradeState.getPositionSize();
        double profit = ((totalProceeds - entryValue) / entryValue) * 100;

        // Update trade state
        tradeState.setInTrade(false);
        var totalProfit = tradeState.getTotalProfit();
        tradeState.setTotalProfit(totalProfit + profit);
        tradeStatePersistenceService.saveTradeState(tradeState);

        // Update capital (add the proceeds after fees)
        currentCapital = portfolio.addToTotalCapital(totalProceeds);

        // Record trade timestamp for cooldown tracking
        recordTradeTimestamp(coinPair);

        circuitBreaker.recordTradeResult(coinPair, profit);
        var circuitState = circuitBreaker.getCircuitState(coinPair);
        log.info("{} {} {} at: {} | Fee: {} | Proceeds: {} | Profit: {}% | Circuit: {}",
            TradeOperationType.SELL,
            coinPair, 
            orderResult.volume(),
            executedPrice,
            orderResult.fee(),
            totalProceeds,
            profit,
            circuitState);
        log.info("{} total Profit: {}%", coinPair, tradeState.getTotalProfit());
      }
    } catch (Exception e) {
      // Use the helper method to handle the error with appropriate logging
      TradeOperationType operationType = inTrade ? TradeOperationType.SELL : TradeOperationType.BUY;
      handleTradingError(coinPair, e, operationType);

      var errorMessage = e.getMessage();
      if (errorMessage != null && errorMessage.contains("Insufficient funds")) {
        if (inTrade) {
          log.info("Detected insufficient funds error during {} for {}. Attempting to resync coin balance with Kraken.",
                  TradeOperationType.SELL, coinPair);
          try {
            resyncCoinBalance(coinPair, tradeState);
          } catch (Exception resyncError) {
            log.error("Failed to resync coin balance for {}: {}", coinPair, resyncError.getMessage());
          }
        } else {
          log.info("Detected insufficient funds error during {} for {}. Attempting to resync USD balance with Kraken.",
                  TradeOperationType.BUY, coinPair);
          try {
            resyncQuoteAssetBalance(coinPair);
          } catch (Exception resyncError) {
            log.error("Failed to resync USD balance for {}: {}", coinPair, resyncError.getMessage());
          }
        }
      }
      
      // If there was an error during buy, make sure we're not left in an inconsistent state
      if (!inTrade && tradeState.isInTrade()) {
        tradeState.setInTrade(false);
        tradeStatePersistenceService.saveTradeState(tradeState);
      }
    }

    log.info("Capital: {}", currentCapital);
  }

  /**
   * Calculates position size as a percentage of capital based on market volatility
   *
   * @param data             Recent price bars
   * @param availableCapital Available capital for position
   * @param params           Strategy parameters
   * @return Recommended position size as percentage of capital
   */
  private double calculateAdaptivePositionSize(String coinPair, List<Bar> data, double entryPrice,
      double availableCapital, StrategyParameters params) {
    // Calculate ATR as percentage of price
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    double atrPercent = (atr / currentPrice) * 100;
    var lowerBound = 2.0;
    var upperBound = 12.0;

    // Base position size (percentage of capital)
    double basePositionSize = 0.5; // Default 50% of capital

    double capitalPercentage;
    // Adjust position size based on volatility
    if (atrPercent < lowerBound) {
      // Low volatility - can take a larger position
      capitalPercentage = Math.min(basePositionSize * 1.5, 1.0);
    } else if (atrPercent > upperBound) {
      // High volatility - reduce position size
      capitalPercentage = basePositionSize * 0.5;
    } else {
      // Normal volatility - use base size
      capitalPercentage = basePositionSize;
    }

    // Account for round-trip fees
    double takerFeeRate = tradingApiService.getCoinTradingFee(coinPair);
    double roundTripFeeImpact = takerFeeRate * 2 / 100; // Both buy and sell
    double feeAdjustedCapitalPercentage = capitalPercentage * (1 - roundTripFeeImpact);

    double calculatedPositionSize = availableCapital * feeAdjustedCapitalPercentage / entryPrice;

    // Ensure the position size meets the minimum order volume requirement
    try {
      double minVolume = tradingApiService.getMinimumOrderVolume(coinPair);
      if (calculatedPositionSize < minVolume) {
        log.info("Calculated position size ({}) is below minimum order volume ({}), using minimum for {}",
                calculatedPositionSize, minVolume, coinPair);
        return minVolume;
      }
    } catch (Exception e) {
      log.warn("Failed to check minimum order volume for {}: {}", coinPair, e.getMessage());
      // Continue with the calculated position size if we can't get minimum volume
    }

    return calculatedPositionSize;
  }

  /**
   * Checks if trading is allowed for the given coin pair based on cooldown period.
   * Thread-safe implementation that prevents rapid consecutive trades.
   *
   * @param coinPair The coin pair to check
   * @return true if trading is allowed, false if still in cooldown
   */
  private boolean canTrade(String coinPair) {
    long currentTime = System.currentTimeMillis();
    Long lastTradeTime = lastTradeTimestamps.get(coinPair);

    if (lastTradeTime == null) {
      return true;
    }

    long tradeCooldownMs = tradeCooldownMinutes * 60L * 1000L;
    long timeSinceLastTrade = currentTime - lastTradeTime;
    boolean canTrade = timeSinceLastTrade >= tradeCooldownMs;

    if (!canTrade) {
      long remainingCooldown = tradeCooldownMs - timeSinceLastTrade;
      log.debug("Trade cooldown active for {}: {} seconds remaining",
          coinPair, remainingCooldown / 1000);
    }

    return canTrade;
  }

  /**
   * Records the timestamp of a successful trade for cooldown tracking.
   * Should be called after every successful buy or sell operation.
   *
   * @param coinPair The coin pair that was traded
   */
  private void recordTradeTimestamp(String coinPair) {
    lastTradeTimestamps.put(coinPair, System.currentTimeMillis());
    log.debug("Trade timestamp recorded for {}", coinPair);
  }

  /**
   * Resyncs the coin balance with Kraken API to ensure the TradeState has the correct position size.
   * This is called when an "insufficient funds" error occurs during a sell operation.
   *
   * @param coinPair   The coin pair (e.g., "PEPE/USD")
   * @param tradeState The current trade state for the coin pair
   * @throws Exception If there's an error fetching the balance from Kraken
   */
  private void resyncCoinBalance(String coinPair, TradeState tradeState) throws Exception {
    // Extract the base asset from the coin pair (e.g., "PEPE" from "PEPE/USD")
    String baseAsset = coinPair.split("/")[0];
    log.info("Resyncing balance for {} (base asset: {})", coinPair, baseAsset);
    
    // Get the actual balance from Kraken
    try {
      Double actualBalance = tradingApiService.getAssetBalance(baseAsset);
      log.info("Actual balance from Kraken for {}: {}", baseAsset, actualBalance);
      
      // Update the trade state with the correct position size
      double oldPositionSize = tradeState.getPositionSize();
      tradeState.setPositionSize(actualBalance.doubleValue());
      tradeStatePersistenceService.saveTradeState(tradeState);
      
      log.info("Updated position size for {} from {} to {}", 
          coinPair, oldPositionSize, actualBalance);
      
      // If the actual balance is zero or very small, we might need to reset the trade state
      if (actualBalance < 0.000001) {
        log.warn("Balance for {} is effectively zero. Resetting trade state.", coinPair);
        tradeState.setInTrade(false);
        tradeStatePersistenceService.saveTradeState(tradeState);
      }
    } catch (Exception e) {
      log.error("Error fetching balance from Kraken for {}: {}", baseAsset, e.getMessage());
      throw e;
    }
  }
  
  /**
   * Handles trading errors in a structured way, avoiding full stacktraces for known error types.
   * 
   * @param coinPair The coin pair where the error occurred
   * @param e The exception that was thrown
   * @param operationType The type of operation (BUY or SELL) where the error occurred
   */
  private void handleTradingError(String coinPair, Exception e, TradeOperationType operationType) {
    String errorMessage = e.getMessage();
    
    if (errorMessage != null) {
      if (errorMessage.contains("Insufficient funds")) {
        log.error("Error executing {} for {}: Insufficient funds", operationType, coinPair);
        return;
      } else if (errorMessage.contains("Invalid arguments")) {
        log.error("Error executing {} for {}: {}", operationType, coinPair, errorMessage);
        return;
      }
    }
    
    // For unknown errors, log with full stacktrace
    log.error("Unexpected error executing {} for {}: {}", operationType, coinPair, errorMessage, e);
  }
  
  /**
   * Resyncs the quote asset (USD) balance with Kraken API to ensure the Portfolio has the correct total capital.
   * This is called when an "insufficient funds" error occurs during a buy operation or on a regular basis.
   *
   * @param coinPair The coin pair (e.g., "PEPE/USD")
   * @throws Exception If there's an error fetching the balance from Kraken
   */
  private void resyncQuoteAssetBalance(String coinPair) throws Exception {
    // Extract the quote asset from the coin pair (e.g., "USD" from "PEPE/USD")
    String quoteAsset = coinPair.split("/")[1];
    log.info("Resyncing balance for {} (quote asset: {})", coinPair, quoteAsset);
    
    // Get the actual balance from Kraken
    try {
      Double actualBalance = tradingApiService.getAssetBalance(quoteAsset);
      log.info("Actual balance from Kraken for {}: {}", quoteAsset, actualBalance);
      
      // Update the portfolio's total capital with the correct balance
      double oldCapital = portfolio.getTotalCapital();
      portfolio.setTotalCapital(actualBalance.doubleValue());
      
      log.info("Updated total capital from {} to {}", oldCapital, actualBalance);
    } catch (Exception e) {
      log.error("Error fetching balance from Kraken for {}: {}", quoteAsset, e.getMessage());
      throw e;
    }
  }
}
