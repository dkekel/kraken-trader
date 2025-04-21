package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.MovingTrendIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiRangeIndicator;
import ch.kekelidze.krakentrader.indicator.SimpleMovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.VolatilityIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component("buyLowSellHighStrategy")
@RequiredArgsConstructor
public class BuyLowSellHighStrategy implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiRangeIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final VolatilityIndicator volatilityIndicator;
  private final SimpleMovingAverageDivergenceIndicator macdIndicator;
  private final VolumeIndicator volumeIndicator;
  private final MovingTrendIndicator movingTrendIndicator;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var bars = context.getBars();
    boolean volatilityOK = volatilityIndicator.isBuySignal(context, params);
    boolean macdConfirmed = macdIndicator.isBuySignal(context, params);
    boolean downtrend = isDowntrend(bars, params);
    boolean bullishSignal = isBullishSignal(context, params);
    boolean movingTrend = movingTrendIndicator.isBuySignal(context, params);
    return downtrend && bullishSignal && volatilityOK && macdConfirmed && movingTrend;
  }

  private boolean isDowntrend(List<Bar> data, StrategyParameters params) {
    var ma20ma50 = movingAverageIndicator.calculateMovingAverage(data,
        params.movingAverageBuyShortPeriod(), params.movingAverageBuyLongPeriod());
    var endIndex = ma20ma50.endIndex();
    var ma20 = ma20ma50.maShort().getValue(endIndex);
    var ma50 = ma20ma50.maLong().getValue(endIndex);
    return ma20.isLessThan(ma50);
  }

  private boolean isBullishSignal(EvaluationContext context, StrategyParameters params) {
    boolean rsiBuySignal = rsiIndicator.isBuySignal(context, params);
    boolean volumeConfirmation = volumeIndicator.isBuySignal(context, params);
    var data = context.getBars();
    boolean hasBullishSequence = hasBullishSequence(data, params);
    return (rsiBuySignal || volumeConfirmation) && hasBullishSequence;
  }

  private boolean hasBullishSequence(List<Bar> data, StrategyParameters params) {
    int bullishCount = 0;

    // Ensure we have enough bars to analyze
    int lookback = params.lookbackPeriod();
    if (data.size() < lookback) {
      return false;
    }

    // Count the number of bullish candles in the lookback period
    for (int i = data.size() - lookback; i < data.size(); i++) {
      if (isBullishCandle(data.get(i))) {
        bullishCount++;
      }
    }

    return bullishCount >= (int) Math.ceil(0.6 * lookback);
  }

  private boolean isBullishCandle(Bar bar) {
    double closePrice = bar.getClosePrice().doubleValue();
    double openPrice = bar.getOpenPrice().doubleValue();
    double highPrice = bar.getHighPrice().doubleValue();
    double lowPrice = bar.getLowPrice().doubleValue();

    // Define bullish candle criteria
    return closePrice > openPrice &&
        (highPrice - closePrice) < 0.1 * (highPrice - lowPrice) &&
        (closePrice - openPrice) > 0.3 * (highPrice - lowPrice);
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    return riskManagementIndicator.isSellSignal(context.getBars(), entryPrice, params);
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(20).movingAverageBuyLongPeriod(50)
//        .movingAverageSellShortPeriod(9).movingAverageSellLongPeriod(26)
        .rsiBuyThreshold(45).rsiSellThreshold(70).rsiPeriod(14).lookbackPeriod(5)
        .macdFastPeriod(12).macdSlowPeriod(26).macdSignalPeriod(9)
//        .adxPeriod(14).adxBullishThreshold(25).adxBearishThreshold(30)
//        .mfiPeriod(20).mfiOversoldThreshold(40).mfiOverboughtThreshold(50)
        .atrPeriod(14).lowVolatilityThreshold(0.9).highVolatilityThreshold(1.5)
        .volumePeriod(24).aboveAverageThreshold(20)
        .lossPercent(3).profitPercent(15)
        .contractionThreshold(3.0)
//        .volumePeriod(20)
//        .aboveAverageThreshold(20)
        .minimumCandles(150)
        .build();
  }
}
