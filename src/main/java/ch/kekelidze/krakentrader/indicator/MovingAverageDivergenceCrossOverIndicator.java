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
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@SuppressWarnings("DuplicatedCode")
@Slf4j
@Component
public class MovingAverageDivergenceCrossOverIndicator implements Indicator {

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    var endIndex = macd.getBarSeries().getEndIndex();
    var macdValue = macd.getValue(endIndex);
    var macdSignalValue = macdSignal.getValue(endIndex);
    var previousMacd = macd.getValue(endIndex - 1);
    var previousMacdSignal = macdSignal.getValue(endIndex - 1);
    log.debug("MACD: {}, Signal: {}, Previous MACD: {}, Previous Signal: {}, Closing Time: {}",
        macdValue.doubleValue(), macdSignalValue.doubleValue(),
        previousMacd.doubleValue(), previousMacdSignal.doubleValue(), data.getLast().getEndTime());
    return previousMacd.isLessThanOrEqual(previousMacdSignal) && macdValue.isGreaterThan(
        macdSignalValue);
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    var endIndex = macd.getBarSeries().getEndIndex();
    var macdValue = macd.getValue(endIndex);
    var macdSignalValue = macdSignal.getValue(endIndex);
    var previousMacd = macd.getValue(endIndex - 1);
    var previousMacdSignal = macdSignal.getValue(endIndex - 1);

    log.debug("MACD: {}, Signal: {}, Previous MACD: {}, Previous Signal: {}, Closing Time: {}",
        macdValue.doubleValue(), macdSignalValue.doubleValue(),
        previousMacd.doubleValue(), previousMacdSignal.doubleValue(), data.getLast().getEndTime());
    return previousMacd.isGreaterThanOrEqual(previousMacdSignal) && macdValue.isLessThan(
        macdSignalValue);
  }

  private MACDIndicator calculateMovingAverageDivergence(List<Bar> pricePeriods,
      StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    return new MACDIndicator(closePrice, params.macdFastPeriod(),
        params.macdSlowPeriod());
  }

  private EMAIndicator calculateMacdSignal(List<Bar> pricePeriods, StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    MACDIndicator macdIndicator = new MACDIndicator(closePrice, params.macdFastPeriod(),
        params.macdSlowPeriod());
    // Calculate EMA of the MACD line using macdSignalPeriod as the signal period
    return new EMAIndicator(macdIndicator, params.macdSignalPeriod());
  }
}
