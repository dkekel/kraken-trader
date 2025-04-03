package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
public class MovingAverageDivergenceIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    return macdSignal > macd;
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double macd = calculateMovingAverageDivergence(data, params);
    var macdSignal = calculateMacdSignal(data, params);
    return macdSignal < macd;
  }

  private double calculateMovingAverageDivergence(List<Bar> pricePeriods,
      StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);

    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    MACDIndicator macd = new MACDIndicator(closePrice, params.shortBarCount(),
        params.longBarCount());
    var latestMacd = macd.getValue(series.getEndIndex()).doubleValue();
    log.debug("Latest MACD: {}", latestMacd);
    return latestMacd;
  }

  private double calculateMacdSignal(List<Bar> pricePeriods, StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    return new EMAIndicator(closePrice, params.macdBarCount()).getValue(series.getEndIndex())
        .doubleValue();
  }
}
