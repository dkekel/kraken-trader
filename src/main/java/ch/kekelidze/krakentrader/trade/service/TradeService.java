package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.IndicatorAgreementStrategy;
import ch.kekelidze.krakentrader.strategy.PricePredictionStrategy;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.WeightedAgreementStrategy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeService {

  private final IndicatorAgreementStrategy indicatorAgreementStrategy;
  private final WeightedAgreementStrategy weightedAgreementStrategy;
  private final PricePredictionStrategy pricePredictionStrategy;

  public void executeStrategy(List<Bar> data, StrategyParameters params) {
    executeSelectedStrategy(data, params, indicatorAgreementStrategy);
  }

  private void executeSelectedStrategy(List<Bar> data, StrategyParameters params,
      Strategy strategy) {
    boolean inTrade = false;
    double[] entryPrice = new double[1];
    double totalProfit = 0;

    // Start after long MA is available
    for (int i = params.movingAverageLongPeriod(); i < data.size(); i++) {
      List<Bar> sublist = data.subList(i - params.movingAverageLongPeriod(), i);
      var currentPrice = data.get(i).getClosePrice().doubleValue();

      if (!inTrade && strategy.shouldBuy(sublist, params)) {
        inTrade = true;
        entryPrice[0] = currentPrice;
        log.info("BUY at: {}", entryPrice);
      } else if (inTrade && strategy.shouldSell(sublist, entryPrice[0], params)) {
        inTrade = false;
        double profit = (currentPrice - entryPrice[0]) / entryPrice[0] * 100;
        totalProfit += profit;
        log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
      }
    }
    log.info("Total Profit: {}%", totalProfit);
  }
}
