package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import java.util.List;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
public class TradeService {

  private static final double PORTFOLIO_ALLOCATION = 1/8d;
  
  private final Portfolio portfolio;
  @Setter
  private Strategy strategy;

  public TradeService(Portfolio portfolio) {
    this.portfolio = portfolio;
  }

  public void executeStrategy(String coinPair, List<Bar> data) {
    executeSelectedStrategy(coinPair, data, strategy.getStrategyParameters(), strategy);
  }

  private void executeSelectedStrategy(String coinPair, List<Bar> data, StrategyParameters params,
      Strategy strategy) {
    var tradeState = portfolio.getOrCreateTradeState(coinPair);
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    double currentCapital = portfolio.getTotalCapital();

    if (currentCapital <= 0) {
      log.info("No capital available to trade {}", coinPair);
    }

    var inTrade = tradeState.isInTrade();
    if (!inTrade && strategy.shouldBuy(data, params)) {
      tradeState.setInTrade(true);
      tradeState.setEntryPrice(currentPrice);
      tradeState.setPositionSize(portfolio.getTotalCapital() * PORTFOLIO_ALLOCATION / currentPrice);
      currentCapital = portfolio.addToTotalCapital(-tradeState.getPositionSize() * currentPrice);
      log.info("BUY {} {} at: {}", coinPair, tradeState.getPositionSize(),
          tradeState.getEntryPrice());
    } else if (inTrade && strategy.shouldSell(data, tradeState.getEntryPrice(), params)) {
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
}
