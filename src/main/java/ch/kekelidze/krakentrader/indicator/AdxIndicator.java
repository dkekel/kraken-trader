package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.indicators.adx.ADXIndicator;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdxIndicator implements Indicator {

  /**
   * Buy only if ADX > 25 (strong trend).
   * @param data price data
   * @param params strategy params
   * @return true if there's strong trend
   */
  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(data).build();
    double adx = calculateADX(series, params.adxPeriod());
    log.debug("ADX: {}, Buy threshold: {}, Closing time: {}", adx, params.adxBullishThreshold(),
        data.getLast().getEndTime());
    return adx > params.adxBullishThreshold();
  }

  /**
   * Ignore sell signals if ADX > 30 (trend likely to continue).
   * @param data price data
   * @param entryPrice asset entry price
   * @param params strategy params
   * @return true if the asset should be sold
   */
  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    BarSeries series = new BaseBarSeriesBuilder().withBars(data).build();
    double adx = calculateADX(series, params.adxPeriod());
    log.debug("ADX: {}, Sell threshold: {}, Closing time: {}", adx, params.adxBearishThreshold(),
        data.getLast().getEndTime());
    return adx < params.adxBearishThreshold();
  }

  public double calculateADX(BarSeries series, int period) {
    ADXIndicator adx = new ADXIndicator(series, period);
    return adx.getValue(series.getEndIndex()).doubleValue();
  }
}
