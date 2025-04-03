package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
public class MovingAverageIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double maShort = calculateMovingAverage(data, params.movingAverageShortPeriod());
    double maLong = calculateMovingAverage(data, params.movingAverageLongPeriod());
    return maCrossesAbove(maShort, maLong);
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double maShort = calculateMovingAverage(data, params.movingAverageShortPeriod());
    double maLong = calculateMovingAverage(data, params.movingAverageLongPeriod());
    return maCrossesAbove(maLong, maShort);
  }

  private double calculateMovingAverage(List<Bar> pricePeriods, int periods) {
    if (pricePeriods.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    SMAIndicator ma = new SMAIndicator(closePrice, periods);
    var latestMa = ma.getValue(series.getEndIndex()).doubleValue();
    log.debug("Latest MA{}: {}", periods, latestMa);
    return latestMa;
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
