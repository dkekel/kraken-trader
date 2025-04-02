package ch.kekelidze.krakentrader.indicator.service;

import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Service
public class IndicatorService {

  public double calculateRSI(List<Bar> pricePeriods, int periods) {
    if (pricePeriods.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    RSIIndicator rsi = new RSIIndicator(closePrice, periods);
    var latestRsi = rsi.getValue(series.getEndIndex()).doubleValue();
    log.debug("Latest RSI{}: {}", periods, latestRsi);
    return latestRsi;
  }

  public double calculateMovingAverage(List<Bar> pricePeriods, int periods) {
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

  public double calculateMovingAverageDivergence(List<Bar> pricePeriods) {
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);

    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    MACDIndicator macd = new MACDIndicator(closePrice, 12, 26); // 12/26 EMAs
    var latestMacd = macd.getValue(series.getEndIndex()).doubleValue();
    log.debug("Latest MACD: {}", latestMacd);
    return latestMacd;
  }

  public double calculateMacdSignal(List<Bar> pricePeriods) {
    BarSeries series = new BaseBarSeriesBuilder().build();
    pricePeriods.forEach(series::addBar);
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    return new EMAIndicator(closePrice, 9).getValue(series.getEndIndex()).doubleValue();
  }
}
