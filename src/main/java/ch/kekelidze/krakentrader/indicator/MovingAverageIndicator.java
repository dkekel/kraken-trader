package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;

@Slf4j
@Component
public class MovingAverageIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    var movingAverage = calculateMovingAverage(data, params);
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    var crossAbove = new CrossIndicator(maShort, maLong);
    return endIndex > 0 && crossAbove.getValue(endIndex);
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var movingAverage = calculateMovingAverage(data, params);
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    var crossBelow = new CrossIndicator(maLong, maShort);
    return endIndex > 0 && crossBelow.getValue(endIndex);
  }

  public MovingAverage calculateMovingAverage(List<Bar> data, StrategyParameters params) {
    if (data.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withBars(data).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    EMAIndicator maShort = new EMAIndicator(closePrice, params.movingAverageShortPeriod());
    EMAIndicator maLong = new EMAIndicator(closePrice, params.movingAverageLongPeriod());
    int endIndex = series.getEndIndex();
    log.debug("MA short: {}, MA long: {}", maShort.getValue(endIndex), maLong.getValue(endIndex));
    return new MovingAverage(maShort, maLong, endIndex);
  }

  public record MovingAverage(EMAIndicator maShort, EMAIndicator maLong, int endIndex) {
  }
}
