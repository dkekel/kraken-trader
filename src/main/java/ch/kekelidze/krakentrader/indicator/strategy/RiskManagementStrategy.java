package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.trade.service.RiskManagementService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class RiskManagementStrategy implements Strategy {

  private final RiskManagementService riskManagementService;

  @Override
  public boolean isBuyTrigger(List<Bar> data, StrategyParameters params) {
    return true;
  }

  @Override
  public boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params) {
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    return riskManagementService.shouldStopLoss(entryPrice, currentPrice, 5)
        || riskManagementService.shouldTakeProfit(entryPrice, currentPrice, 10);
  }
}
