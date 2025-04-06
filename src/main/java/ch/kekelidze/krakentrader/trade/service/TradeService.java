package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.IndicatorAgreementStrategy;
import ch.kekelidze.krakentrader.strategy.MovingAverageScalper;
import ch.kekelidze.krakentrader.strategy.PricePredictionStrategy;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.WeightedAgreementStrategy;
import ch.kekelidze.krakentrader.trade.TradeState;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

  private final TradeState tradeState;

  private final IndicatorAgreementStrategy indicatorAgreementStrategy;
  private final WeightedAgreementStrategy weightedAgreementStrategy;
  private final MovingAverageScalper movingAverageScalper;

  public void executeStrategy(List<Bar> data) {
    var params = movingAverageScalper.getStrategyParameters();
    executeSelectedStrategy(data, params, movingAverageScalper);
  }

  private void executeSelectedStrategy(List<Bar> data, StrategyParameters params,
      Strategy strategy) {
    var currentPrice = data.getLast().getClosePrice().doubleValue();

    var inTrade = tradeState.isInTrade();
    if (!inTrade && strategy.shouldBuy(data, params)) {
      tradeState.setInTrade(true);
      tradeState.setEntryPrice(currentPrice);
      tradeState.setPositionSize(tradeState.getCapital() / currentPrice);
      log.info("BUY at: {}", tradeState.getEntryPrice());
    } else if (inTrade && strategy.shouldSell(data, tradeState.getEntryPrice(), params)) {
      tradeState.setInTrade(false);
      var entryPrice = tradeState.getEntryPrice();
      double profit = (currentPrice - entryPrice) / entryPrice * 100;
      var totalProfit = tradeState.getTotalProfit();
      tradeState.setTotalProfit(totalProfit + profit);
      tradeState.setCapital(tradeState.getPositionSize() * currentPrice);
      log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
    }
    log.info("Total Profit: {}%", tradeState.getTotalProfit());
    log.info("Capital: {}", tradeState.getCapital());
  }
}
