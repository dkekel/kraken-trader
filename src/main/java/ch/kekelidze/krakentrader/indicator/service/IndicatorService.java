package ch.kekelidze.krakentrader.indicator.service;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DoubleNum;

@Slf4j
@Service
public class IndicatorService {

  public double calculateRSI(String coin, List<Double> closes, int periods) {
    if (closes.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withName(coin).build();
    closes.forEach(price -> series.addPrice(DoubleNum.valueOf(price)));
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    RSIIndicator rsi = new RSIIndicator(closePrice, periods);
    var latestRsi = rsi.getValue(series.getEndIndex()).doubleValue();
    log.info("Latest RSI{} for {}: {}", periods, coin, latestRsi);
    return latestRsi;
  }

  public double calculateMovingAverage(String coin, List<Double> closes, int periods) {
    if (closes.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withName(coin).build();
    closes.forEach(price -> series.addPrice(DoubleNum.valueOf(price)));
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    SMAIndicator ma = new SMAIndicator(closePrice, periods);
    var latestMa = ma.getValue(series.getEndIndex()).doubleValue();
    log.info("Latest MA{} for {}: {}", periods, coin, latestMa);
    return latestMa;
  }
}
