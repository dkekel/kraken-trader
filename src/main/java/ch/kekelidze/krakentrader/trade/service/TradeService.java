package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.api.dto.OrderResult;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.Portfolio;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
public class TradeService {

  private double portfolioAllocation = 1/8d;

  private final Map<String, Object> coinPairLocks = new ConcurrentHashMap<>();

  private final AtrAnalyser atrAnalyser;
  private final Portfolio portfolio;
  private final TradeStatePersistenceService tradeStatePersistenceService;
  private final TradingApiService tradingApiService;
  @Getter
  @Setter
  private Strategy strategy;

  public TradeService(AtrAnalyser atrAnalyser, Portfolio portfolio,
      TradeStatePersistenceService tradeStatePersistenceService,
      TradingApiService tradingApiService) {
    this.atrAnalyser = atrAnalyser;
    this.portfolio = portfolio;
    this.tradeStatePersistenceService = tradeStatePersistenceService;
    this.tradingApiService = tradingApiService;
  }

  public void executeStrategy(String coinPair, List<Bar> data) {
    Object lock = coinPairLocks.computeIfAbsent(coinPair, k -> new Object());
    synchronized (lock) {
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
      if (!inTrade && strategy.shouldBuy(evaluationContext, params)) {
        // Calculate position size based on allocated capital
        var allocatedCapital = portfolio.getTotalCapital() * portfolioAllocation;
        var positionSize = calculateAdaptivePositionSize(coinPair, data, currentPrice,
            allocatedCapital, params);

        // Place market buy order
        OrderResult orderResult = tradingApiService.placeMarketBuyOrder(coinPair, positionSize);

        // Set trade state with actual executed values
        tradeState.setInTrade(true);

        // Calculate entry price including fees
        var executedPrice =
            orderResult.executedPrice() == 0 ? currentPrice : orderResult.executedPrice();
        double totalCost = executedPrice * orderResult.volume() + orderResult.fee();
        double entryPriceWithFees = totalCost / orderResult.volume();
        tradeState.setEntryPrice(entryPriceWithFees);

        // Use actual executed volume from the order
        tradeState.setPositionSize(orderResult.volume());
        tradeStatePersistenceService.saveTradeState(tradeState);

        // Update capital (deduct the total cost including fees)
        currentCapital = portfolio.addToTotalCapital(-totalCost);

        log.info("BUY {} {} at: {} (including fees: {}) | Fee: {}", 
            coinPair, 
            orderResult.volume(),
            executedPrice,
            entryPriceWithFees,
            orderResult.fee());

      } else if (inTrade && strategy.shouldSell(evaluationContext, tradeState.getEntryPrice(),
          params)) {
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

        log.info("SELL {} {} at: {} | Fee: {} | Proceeds: {} | Profit: {}%", 
            coinPair, 
            orderResult.volume(),
            executedPrice,
            orderResult.fee(),
            totalProceeds,
            profit);
        log.info("{} total Profit: {}%", coinPair, tradeState.getTotalProfit());
      }
    } catch (Exception e) {
      log.error("Error executing trade for {}: {}", coinPair, e.getMessage(), e);
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
}
