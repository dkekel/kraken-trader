package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class RsiStrategy implements Strategy {

  private final IndicatorService indicatorService;

  @Override
  public boolean isBuyTrigger(List<Bar> data, StrategyParameters params) {
    double rsi = indicatorService.calculateRSI(data, params.rsiPeriod());
    return rsi < params.rsiBuyThreshold();
  }

  @Override
  public boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params) {
    double rsi = indicatorService.calculateRSI(data, params.rsiPeriod());
    return rsi > params.rsiSellThreshold();
  }
}
