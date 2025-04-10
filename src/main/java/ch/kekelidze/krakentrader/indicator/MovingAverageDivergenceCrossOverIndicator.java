package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
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
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    double previousMacd = calculateMovingAverageDivergence(data.subList(0, data.size() - 1),
        params);
    double previousMacdSignal = calculateMacdSignal(data.subList(0, data.size() - 1), params);
    log.debug("MACD: {}, Signal: {}, Previous MACD: {}, Previous Signal: {}, Closing Time: {}",
        macd, macdSignal,
        previousMacd, previousMacdSignal, data.getLast().getEndTime());
    return previousMacd <= previousMacdSignal && macd > macdSignal;
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    double previousMacd = calculateMovingAverageDivergence(data.subList(0, data.size() - 1),
        params);
    double previousMacdSignal = calculateMacdSignal(data.subList(0, data.size() - 1), params);

    log.debug("MACD: {}, Signal: {}, Previous MACD: {}, Previous Signal: {}, Closing Time: {}",
        macd, macdSignal,
        previousMacd, previousMacdSignal, data.getLast().getEndTime());
    return previousMacd >= previousMacdSignal && macd < macdSignal;
  }

  private double calculateMovingAverageDivergence(List<Bar> pricePeriods,
      StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    MACDIndicator macd = new MACDIndicator(closePrice, params.macdFastPeriod(),
        params.macdSlowPeriod());
    return macd.getValue(series.getEndIndex()).doubleValue();
  }

  private double calculateMacdSignal(List<Bar> pricePeriods, StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    MACDIndicator macdIndicator = new MACDIndicator(closePrice, params.macdFastPeriod(),
        params.macdSlowPeriod());
    // Calculate EMA of the MACD line using macdSignalPeriod as the signal period
    EMAIndicator macdSignal = new EMAIndicator(macdIndicator, params.macdSignalPeriod());
    return macdSignal.getValue(series.getEndIndex()).doubleValue();

  }
}
