package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.strategy.PricePredictionStrategy;
import ch.kekelidze.krakentrader.indicator.strategy.Strategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

  private final List<Strategy> strategies;

  public void executeStrategy(List<Bar> data, StrategyParameters params) {
    boolean inTrade = false;
    double[] entryPrice = new double[1];
    double totalProfit = 0;

    // Start after long MA is available
    for (int i = params.movingAverageLongPeriod(); i < data.size(); i++) {
      List<Bar> sublist = data.subList(i - params.movingAverageLongPeriod(), i);
      var currentPrice = data.get(i).getClosePrice().doubleValue();

      if (!inTrade && strategies.stream().allMatch(s -> s.isBuyTrigger(sublist, params))) {
        inTrade = true;
        entryPrice[0] = currentPrice;
        log.info("BUY at: {}", entryPrice);
      } else if (inTrade
          && strategies.stream().anyMatch(s -> s.isSellTrigger(sublist, entryPrice[0], params))) {
        inTrade = false;
        double profit = (currentPrice - entryPrice[0]) / entryPrice[0] * 100;
        totalProfit += profit;
        log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
      }
    }
    log.info("Total Profit: {}%", totalProfit);
  }
}
