package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class MovingAverageDivergenceStrategy implements Strategy {

  private final IndicatorService indicatorService;

  @Override
  public boolean isBuyTrigger(List<Bar> data, StrategyParameters params) {
    double macd = indicatorService.calculateMovingAverageDivergence(data);
    var macdSignal = indicatorService.calculateMacdSignal(data);
    return macdSignal > macd;
  }

  @Override
  public boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params) {
    double macd = indicatorService.calculateMovingAverageDivergence(data);
    var macdSignal = indicatorService.calculateMacdSignal(data);
    return macdSignal < macd;
  }
}
