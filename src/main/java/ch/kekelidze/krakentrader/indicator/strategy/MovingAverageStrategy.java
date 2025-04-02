package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class MovingAverageStrategy implements Strategy {

  private final IndicatorService indicatorService;

  @Override
  public boolean isBuyTrigger(List<Bar> data, StrategyParameters params) {
    double maShort = indicatorService.calculateMovingAverage(data,
        params.movingAverageShortPeriod());
    double maLong = indicatorService.calculateMovingAverage(data,
        params.movingAverageLongPeriod());
    return maCrossesAbove(maShort, maLong);
  }

  @Override
  public boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params) {
    double maShort = indicatorService.calculateMovingAverage(data,
        params.movingAverageShortPeriod());
    double maLong = indicatorService.calculateMovingAverage(data,
        params.movingAverageLongPeriod());
    return maCrossesAbove(maLong, maShort);
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
