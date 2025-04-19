package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
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
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var movingAverage = calculateMovingAverage(data, params.movingAverageBuyShortPeriod(),
        params.movingAverageBuyLongPeriod());
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    return endIndex > 0 &&
        maShort.getValue(endIndex).isGreaterThan(maLong.getValue(endIndex)) &&
        maShort.getValue(endIndex - 1).isLessThanOrEqual(maLong.getValue(endIndex - 1));
  }

  public boolean isMa50Below100(List<Bar> data) {
    var movingAverage = calculateMovingAverage(data, 50, 100);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isLessThan(ma100.getValue(endIndex));
  }

  public boolean isMa100Below200(List<Bar> data) {
    var movingAverage = calculateMovingAverage(data, 100, 200);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isLessThan(ma200.getValue(endIndex));
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var movingAverage = calculateMovingAverage(data, params.movingAverageSellShortPeriod(),
        params.movingAverageSellLongPeriod());
    var maShort = movingAverage.maShort();
    var maLong = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();

    return endIndex > 0 &&
        maLong.getValue(endIndex).isGreaterThan(maShort.getValue(endIndex)) &&
        maLong.getValue(endIndex - 1).isLessThanOrEqual(maShort.getValue(endIndex - 1));

  }

  public boolean isMa50GreaterThan100(List<Bar> data) {
    var movingAverage = calculateMovingAverage(data, 50, 100);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isGreaterThan(ma100.getValue(endIndex));
  }

  public boolean isMa100GreaterThan200(List<Bar> data) {
    var movingAverage = calculateMovingAverage(data, 100, 200);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isGreaterThan(ma200.getValue(endIndex));
  }

  public MovingAverage calculateMovingAverage(List<Bar> data, int shortPeriod, int longPeriod) {
    if (data.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withBars(data).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    EMAIndicator maShort = new EMAIndicator(closePrice, shortPeriod);
    EMAIndicator maLong = new EMAIndicator(closePrice, longPeriod);
    int endIndex = series.getEndIndex();
    log.debug("MA short: {}, MA long: {}, Closing Time: {}", maShort.getValue(endIndex),
        maLong.getValue(endIndex), data.get(endIndex).getEndTime());
    return new MovingAverage(maShort, maLong, endIndex);
  }

  /**
   * Checks if two moving averages are within a specified percentage threshold of each other.
   *
   * @param movingAverages The short and the long moving average values
   * @param thresholdPercent The percentage threshold (e.g., 1.0 for 1%)
   * @return True if the moving averages are within the threshold, false otherwise
   */
  public boolean areMovingAveragesWithinThreshold(MovingAverage movingAverages,
      double thresholdPercent) {
    // Calculate the percentage difference
    var endIndex = movingAverages.endIndex();
    double maShort = movingAverages.maShort().getValue(endIndex).doubleValue();
    double maLong = movingAverages.maLong().getValue(endIndex).doubleValue();
    double percentDifference = Math.abs(maShort - maLong) / maLong * 100.0;

    // Check if the difference is less than or equal to the threshold
    return percentDifference <= thresholdPercent;
  }

  public record MovingAverage(EMAIndicator maShort, EMAIndicator maLong, int endIndex) {

  }
}
