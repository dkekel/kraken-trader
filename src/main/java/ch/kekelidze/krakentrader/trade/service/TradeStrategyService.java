package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeStrategyService {

  private static double entryPrice = 0;
  private static boolean inTrade = false;

  private final KrakenApiService krakenApiService;
  private final IndicatorService indicatorService;
  private final RiskManagementService riskManagementService;

  public void executeStrategy(String coin, List<Bar> closes, double currentPrice) {
    double ma9 = indicatorService.calculateMovingAverage(coin,
        closes.subList(closes.size() - 9, closes.size()), 9);
    double ma21 = indicatorService.calculateMovingAverage(coin,
        closes.subList(closes.size() - 21, closes.size()), 21);
    double rsi = indicatorService.calculateRSI(coin,
        closes.subList(closes.size() - 14, closes.size()), 14);

    // Buy Signal: MA9 > MA21 + RSI < 30
    if (!inTrade && maCrossesAbove(ma9, ma21) && rsi < 30) {
      inTrade = true;
      entryPrice = currentPrice;
      log.info("BUY at: {}", entryPrice);
      // KrakenTradeAPI.buyXRP(entryPrice); // Uncomment to execute live trades
    }
    // Sell Signal: MA9 < MA21 + RSI > 70 + Stop-Loss/Take-Profit
    else if (inTrade && (maCrossesAbove(ma21, ma9) || rsi > 70 ||
        riskManagementService.shouldStopLoss(entryPrice, currentPrice, 5) ||
        riskManagementService.shouldTakeProfit(entryPrice, currentPrice, 10))) {
      inTrade = false;
      double profit = (currentPrice - entryPrice) / entryPrice * 100;
      log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
    }
  }

  /**
   * Checks if the trading conditions are met for the given coin and places a limit order if true.
   * The conditions are: 1. MA(9) crosses above MA(50) at 1-hour candles. 2. MA(50) is below MA(100)
   * at 1-hour candles. 3. MA(100) is below MA(200) at 1-hour candles.
   */
  public void checkTradingConditionsAndExecute(String coin, int period) {
    // Example stub for querying historical data
    List<Bar> prices = krakenApiService.queryHistoricalData(coin, period);

    double ma9 = indicatorService.calculateMovingAverage(coin, prices, 9);
    double ma21 = indicatorService.calculateMovingAverage(coin, prices, 21);

    // Check crossover conditions
    if (maCrossesAbove(ma9, ma21)) {
      log.info("BUY SIGNAL: MA9 ({}) crossed above MA21 ({})", ma9, ma21);
      // Place buy order via Kraken's Trade API (example)
      // KrakenTradeAPI.buyXRP();
    } else if (maCrossesAbove(ma21, ma9)) {
      log.info("SELL SIGNAL: MA9 ({}) crossed below MA21 ({})", ma9, ma21);
      // KrakenTradeAPI.sellXRP();
    } else {
      log.info("HOLD: No crossover detected.");
    }
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
