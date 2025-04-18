package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
public class MovingAverageIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    var movingAverage = calculateMovingAverage(data, params);
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    return endIndex > 0 &&
        maShort.getValue(endIndex).isGreaterThan(maLong.getValue(endIndex)) &&
        maShort.getValue(endIndex-1).isLessThanOrEqual(maLong.getValue(endIndex-1));
  }

  public boolean isMa50Below100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isLessThan(ma100.getValue(endIndex));
  }

  public boolean isMa100Below200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isLessThan(ma200.getValue(endIndex));
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var movingAverage = calculateMovingAverage(data, params);
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    return endIndex > 0 &&
        maLong.getValue(endIndex).isGreaterThan(maShort.getValue(endIndex)) &&
        maLong.getValue(endIndex-1).isLessThanOrEqual(maShort.getValue(endIndex-1));

  }

  public boolean isMa50GreaterThan100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isGreaterThan(ma100.getValue(endIndex));
  }

  public boolean isMa100GreaterThan200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isGreaterThan(ma200.getValue(endIndex));
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
    log.debug("MA short: {}, MA long: {}, Closing Time: {}", maShort.getValue(endIndex),
        maLong.getValue(endIndex), data.get(endIndex).getEndTime());
    return new MovingAverage(maShort, maLong, endIndex);
  }

  public record MovingAverage(EMAIndicator maShort, EMAIndicator maLong, int endIndex) {
  }
}
