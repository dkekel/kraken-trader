package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.Portfolio;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
public class TradeService {

  private static final double PORTFOLIO_ALLOCATION = 1/8d;

  private final AtrAnalyser atrAnalyser;
  private final Portfolio portfolio;
  @Getter
  @Setter
  private Strategy strategy;

  public TradeService(AtrAnalyser atrAnalyser, Portfolio portfolio) {
    this.atrAnalyser = atrAnalyser;
    this.portfolio = portfolio;
  }

  public void executeStrategy(String coinPair, List<Bar> data) {
    executeSelectedStrategy(coinPair, data, strategy.getStrategyParameters(coinPair), strategy);
  }

  private void executeSelectedStrategy(String coinPair, List<Bar> data, StrategyParameters params,
      Strategy strategy) {
    var tradeState = portfolio.getOrCreateTradeState(coinPair);
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    double currentCapital = portfolio.getTotalCapital();

    if (currentCapital <= 0) {
      log.info("No capital available to trade {}", coinPair);
    }

    var evaluationContext = EvaluationContext.builder().symbol(coinPair).bars(data).build();
    var inTrade = tradeState.isInTrade();
    if (!inTrade && strategy.shouldBuy(evaluationContext, params)) {
      var allocatedCapital = portfolio.getTotalCapital() * PORTFOLIO_ALLOCATION;
      tradeState.setInTrade(true);
      tradeState.setEntryPrice(currentPrice);
      var positionSize = calculateAdaptivePositionSize(data, currentPrice, allocatedCapital,
          params);
      tradeState.setPositionSize(positionSize);
      currentCapital = portfolio.addToTotalCapital(-tradeState.getPositionSize() * currentPrice);
      log.info("BUY {} {} at: {}", coinPair, tradeState.getPositionSize(),
          tradeState.getEntryPrice());
    } else if (inTrade && strategy.shouldSell(evaluationContext, tradeState.getEntryPrice(),
        params)) {
      tradeState.setInTrade(false);
      var entryPrice = tradeState.getEntryPrice();
      double profit = (currentPrice - entryPrice) / entryPrice * 100;
      var totalProfit = tradeState.getTotalProfit();
      tradeState.setTotalProfit(totalProfit + profit);
      currentCapital = portfolio.addToTotalCapital(tradeState.getPositionSize() * currentPrice);
      log.info("SELL {} {} at: {} | Profit: {}%", coinPair, tradeState.getPositionSize(),
          currentPrice, profit);
      log.info("{} total Profit: {}%", coinPair, tradeState.getTotalProfit());
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
  private double calculateAdaptivePositionSize(List<Bar> data, double entryPrice,
      double availableCapital, StrategyParameters params) {
    // Calculate ATR as percentage of price
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    double atrPercent = (atr / currentPrice) * 100;
    var lowerBound = 2.0;
    var upperBound = 12.0;

    // Base position size (percentage of capital)
    double basePositionSize = 0.5; // Default 60% of capital

    double capitalPercentage;
    // Adjust position size based on volatility
    if (atrPercent < lowerBound) {
      // Low volatility - can take larger position
      capitalPercentage = Math.min(basePositionSize * 1.5, 1.0);
    } else if (atrPercent > upperBound) {
      // High volatility - reduce position size
      capitalPercentage = basePositionSize * 0.5;
    } else {
      // Normal volatility - use base size
      capitalPercentage = basePositionSize;
    }
    return availableCapital * capitalPercentage / entryPrice;
  }
}
