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

  //TODO Q4 market
//  @Override
//  public StrategyParameters getStrategyParameters() {
//    return StrategyParameters.builder()
//        .movingAverageBuyShortPeriod(21).movingAverageBuyLongPeriod(75)
//        .rsiBuyThreshold(48).rsiSellThreshold(79).rsiPeriod(17).lookbackPeriod(6)
//        .macdFastPeriod(11).macdSlowPeriod(25).macdSignalPeriod(7)
//        .atrPeriod(15).lowVolatilityThreshold(0.87).highVolatilityThreshold(1.3)
//        .volumePeriod(27).aboveAverageThreshold(23)
//        .lossPercent(3.1).profitPercent(8.9)
//        .contractionThreshold(3.4)
//        .minimumCandles(225)
//        .build();
//  }

  //TODO Q3
  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(15).movingAverageBuyLongPeriod(53)
        .rsiPeriod(13).rsiBuyThreshold(42).rsiSellThreshold(66)
        .macdFastPeriod(13).macdSlowPeriod(20).macdSignalPeriod(8)
        .volumePeriod(24).aboveAverageThreshold(39)
        .lossPercent(2).profitPercent(17.8)
        .contractionThreshold(4.1)
        .atrPeriod(17).lookbackPeriod(7)
        .lowVolatilityThreshold(0.87).highVolatilityThreshold(1.32)
        .minimumCandles(159)
        .build();
  }
}
