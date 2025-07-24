package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.api.dto.OrderResult;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.TradeState;
import ch.kekelidze.krakentrader.trade.util.TradingCircuitBreaker;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
public class TradeService {

  private double portfolioAllocation = 1/8d;

  private final Map<String, Object> coinPairLocks = new ConcurrentHashMap<>();
  private final Map<String, Long> lastTradeTimestamps = new ConcurrentHashMap<>();

  @Value("${trading.cooldown.minutes:15}")
  private int tradeCooldownMinutes;


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
    log.info("Circuit breaker protection enabled");
  }

  public void executeStrategy(String coinPair, List<Bar> data) {
    Object lock = coinPairLocks.computeIfAbsent(coinPair, k -> new Object());
    synchronized (lock) {
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
   * Sets the portfolio allocation based on the number of coin pairs.
   * Each coin pair gets an equal portion of the total portfolio capital.
   *
   * @param numberOfCoinPairs The number of coin pairs being traded
   */
  public void setPortfolioAllocation(int numberOfCoinPairs) {
    if (numberOfCoinPairs <= 0) {
      throw new IllegalArgumentException("Number of coin pairs must be greater than 0");
    }
    this.portfolioAllocation = 1.0 / numberOfCoinPairs;
    log.info("Portfolio allocation set to 1/{} = {} for each coin pair",
        numberOfCoinPairs, this.portfolioAllocation);
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
            "Conflicting BUY and SELL signals detected for {} - skipping trade to avoid whipsaw",
            coinPair);
        return;
      }

      if (!inTrade && buySignal) {
        // Calculate position size based on allocated capital
        var allocatedCapital = portfolio.getTotalCapital() * portfolioAllocation;
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
            "BUY {} {} at: {} (including fees: {}) | Fee: {} | Circuit: {}",
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
        log.info("SELL {} {} at: {} | Fee: {} | Proceeds: {} | Profit: {}% | Circuit: {}",
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
      log.error("Error executing trade for {}: {}", coinPair, e.getMessage(), e);
      
      // Check if this is an "insufficient funds" error
      if (e.getMessage() != null && e.getMessage().contains("Insufficient funds")) {
        if (inTrade) {
          // Handle insufficient funds during sell operation (base asset)
          log.info("Detected insufficient funds error during SELL for {}. Attempting to resync coin balance with Kraken.", coinPair);
          try {
            resyncCoinBalance(coinPair, tradeState);
          } catch (Exception resyncError) {
            log.error("Failed to resync coin balance for {}: {}", coinPair, resyncError.getMessage(), resyncError);
          }
        } else {
          // Handle insufficient funds during buy operation (quote asset - USD)
          log.info("Detected insufficient funds error during BUY for {}. Attempting to resync USD balance with Kraken.", coinPair);
          try {
            resyncQuoteAssetBalance(coinPair);
          } catch (Exception resyncError) {
            log.error("Failed to resync USD balance for {}: {}", coinPair, resyncError.getMessage(), resyncError);
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

    return availableCapital * feeAdjustedCapitalPercentage / entryPrice;
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
   * Resyncs the quote asset (USD) balance with Kraken API to ensure the Portfolio has the correct total capital.
   * This is called when an "insufficient funds" error occurs during a buy operation.
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
