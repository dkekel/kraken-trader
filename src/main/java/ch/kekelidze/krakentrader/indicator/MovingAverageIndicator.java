package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.log.GrafanaLogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovingAverageIndicator implements Indicator {

  private final GrafanaLogService grafanaLogService;

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double maShort = calculateMovingAverage(data, params.movingAverageShortPeriod());
    double maLong = calculateMovingAverage(data, params.movingAverageLongPeriod());
    log.debug("MA short: {}, MA long: {}", maShort, maLong);
    grafanaLogService.log("MA short: " + maShort + ", MA long: " + maLong);
    return maCrossesAbove(maShort, maLong);
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double maShort = calculateMovingAverage(data, params.movingAverageShortPeriod());
    double maLong = calculateMovingAverage(data, params.movingAverageLongPeriod());
    log.debug("MA short: {}, MA long: {}", maShort, maLong);
    grafanaLogService.log("MA short: " + maShort + ", MA long: " + maLong);
    return maCrossesAbove(maLong, maShort);
  }

  private double calculateMovingAverage(List<Bar> pricePeriods, int periods) {
    if (pricePeriods.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    EMAIndicator ma = new EMAIndicator(closePrice, periods);
    return ma.getValue(series.getEndIndex()).doubleValue();
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
