package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
public class RiskManagementIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    return true;
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    var stopLossTakeProfit = shouldStopLoss(entryPrice, currentPrice, params.lossPercent())
        || shouldTakeProfit(entryPrice, currentPrice, params.profitPercent());
    log.debug("Shot stop loss/take profit: {} | Closing time: {}", stopLossTakeProfit,
        data.getLast().getEndTime());
    return stopLossTakeProfit;
  }

  private boolean shouldStopLoss(double entryPrice, double currentPrice, double lossPercent) {
    return currentPrice <= entryPrice * (1 - lossPercent / 100);
  }

  private boolean shouldTakeProfit(double entryPrice, double currentPrice,
      double profitPercent) {
    return currentPrice >= entryPrice * (1 + profitPercent / 100);
  }
}
