package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;

@Slf4j
@Component
public class RsiIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double rsi = calculateRSI(data, params.rsiPeriod());
    log.debug("RSI: {}, Buy threshold: {}, Closing time: {}", rsi, params.rsiBuyThreshold(),
        data.getLast().getEndTime());
    return rsi < params.rsiBuyThreshold();
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double rsi = calculateRSI(data, params.rsiPeriod());
    log.debug("RSI: {}, Sell threshold: {}, Closing time: {}", rsi, params.rsiSellThreshold(),
        data.getLast().getEndTime());
    return rsi > params.rsiSellThreshold();
  }

  private double calculateRSI(List<Bar> pricePeriods, int periods) {
    if (pricePeriods.isEmpty()) {
      throw new IllegalArgumentException("No data available for the given coin and period.");
    }
    BarSeries series = new BaseBarSeriesBuilder().withBars(pricePeriods).build();
    ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
    RSIIndicator rsi = new RSIIndicator(closePrice, periods);
    return rsi.getValue(series.getEndIndex()).doubleValue();
  }
}
