package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import ch.kekelidze.krakentrader.indicator.service.strategy.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeStrategyService {

  private final IndicatorService indicatorService;
  private final RiskManagementService riskManagementService;

  public void executeStrategy(List<Bar> data, StrategyParameters params) {
    boolean inTrade = false;
    double entryPrice = 0;
    double totalProfit = 0;

    // Start after long MA is available
    for (int i = params.movingAverageLongPeriod(); i < data.size(); i++) {
      List<Bar> sublist = data.subList(i - params.movingAverageLongPeriod(), i);
      double maShort = indicatorService.calculateMovingAverage(sublist,
          params.movingAverageShortPeriod());
      double maLong = indicatorService.calculateMovingAverage(sublist,
          params.movingAverageLongPeriod());
      double rsi = indicatorService.calculateRSI(sublist, params.rsiPeriod());

      var currentPrice = data.get(i).getClosePrice().doubleValue();

      if (!inTrade && maCrossesAbove(maShort, maLong) && rsi < params.rsiBuyThreshold()) {
        inTrade = true;
        entryPrice = currentPrice;
        log.info("BUY at: {}", entryPrice);
      } else if (inTrade && (maCrossesAbove(maLong, maShort) || rsi > params.rsiSellThreshold()
          || riskManagementService.shouldStopLoss(entryPrice, currentPrice, 5)
          || riskManagementService.shouldTakeProfit(entryPrice, currentPrice, 10))) {
        inTrade = false;
        double profit = (currentPrice - entryPrice) / entryPrice * 100;
        totalProfit += profit;
        log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
      }
    }
    log.info("Total Profit: {}%", totalProfit);
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
