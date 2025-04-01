package ch.kekelidze.krakentrader.backtester.service;

import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackTesterService {

  private final IndicatorService indicatorService;

  public void backtest(List<Double> closes) {
    boolean inTrade = false;
    double entryPrice = 0;
    double totalProfit = 0;

    for (int i = 21; i < closes.size(); i++) { // Start after MA21 is available
      List<Double> sublist = closes.subList(i - 21, i);
      double ma9 = indicatorService.calculateMovingAverage("test-coin", sublist, 9);
      double ma21 = indicatorService.calculateMovingAverage("test-coin", sublist, 21);

      if (!inTrade && ma9 > ma21) {
        inTrade = true;
        entryPrice = closes.get(i);
        log.info("BUY at: {}", entryPrice);
      } else if (inTrade && ma9 < ma21) {
        inTrade = false;
        double exitPrice = closes.get(i);
        double profit = (exitPrice - entryPrice) / entryPrice * 100;
        totalProfit += profit;
        log.info("SELL at: {}} | Profit: {}%", exitPrice, profit);
      }
    }
    log.info("Total Profit: {}%", totalProfit);
  }
}
