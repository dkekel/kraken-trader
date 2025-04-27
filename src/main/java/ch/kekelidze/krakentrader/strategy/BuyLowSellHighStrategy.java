package ch.kekelidze.krakentrader.strategy;

import static ch.kekelidze.krakentrader.strategy.service.StrategyParametersService.getValidCoinName;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.MovingTrendIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiRangeIndicator;
import ch.kekelidze.krakentrader.indicator.SimpleMovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.VolatilityIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component("buyLowSellHighStrategy")
public class BuyLowSellHighStrategy implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiRangeIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final VolatilityIndicator volatilityIndicator;
  private final SimpleMovingAverageDivergenceIndicator macdIndicator;
  private final VolumeIndicator volumeIndicator;
  private final MovingTrendIndicator movingTrendIndicator;
  private final StrategyParametersService strategyParametersService;

  public BuyLowSellHighStrategy(MovingAverageIndicator movingAverageIndicator,
      RsiRangeIndicator rsiIndicator, RiskManagementIndicator riskManagementIndicator,
      VolatilityIndicator volatilityIndicator, SimpleMovingAverageDivergenceIndicator macdIndicator,
      VolumeIndicator volumeIndicator, MovingTrendIndicator movingTrendIndicator,
      StrategyParametersService strategyParametersService) {
    this.movingAverageIndicator = movingAverageIndicator;
    this.rsiIndicator = rsiIndicator;
    this.riskManagementIndicator = riskManagementIndicator;
    this.volatilityIndicator = volatilityIndicator;
    this.macdIndicator = macdIndicator;
    this.volumeIndicator = volumeIndicator;
    this.movingTrendIndicator = movingTrendIndicator;
    this.strategyParametersService = strategyParametersService;
  }

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    boolean volatilityOK = volatilityIndicator.isBuySignal(context, params);
    boolean macdConfirmed = macdIndicator.isBuySignal(context, params);
    boolean downtrend = isDowntrend(context, params);
    boolean bullishSignal = isBullishSignal(context, params);
    boolean movingTrend = movingTrendIndicator.isBuySignal(context, params);

    log.debug(
        "Buy '{}' signals - Volatility: {}, MACD: {}, Downtrend: {}, Bullish: {}, MovingTrend: {}",
        context.getSymbol(), volatilityOK, macdConfirmed, downtrend, bullishSignal, movingTrend);

    return downtrend && bullishSignal && volatilityOK && macdConfirmed && movingTrend;
  }

  private boolean isDowntrend(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var ma20ma50 = movingAverageIndicator.calculateMovingAverage(data,
        params.movingAverageBuyShortPeriod(), params.movingAverageBuyLongPeriod());
    var endIndex = ma20ma50.endIndex();
    var ma20 = ma20ma50.maShort().getValue(endIndex);
    var ma50 = ma20ma50.maLong().getValue(endIndex);
    log.debug("Downtrend '{}' - MA20: {}, MA50: {}", context.getSymbol(), ma20, ma50);
    return ma20.isLessThan(ma50);
  }

  private boolean isBullishSignal(EvaluationContext context, StrategyParameters params) {
    boolean rsiBuySignal = rsiIndicator.isBuySignal(context, params);
    boolean volumeConfirmation = volumeIndicator.isBuySignal(context, params);
    var data = context.getBars();
    boolean hasBullishSequence = hasBullishSequence(data, params);

    log.debug("Bullish '{}' signal evaluation - RSI: {}, Volume: {}, Bullish Sequence: {}",
        context.getSymbol(), rsiBuySignal, volumeConfirmation, hasBullishSequence);

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
    boolean riskSignal = riskManagementIndicator.isSellSignal(context.getBars(), entryPrice,
        params);
    log.debug("Sell '{}' signals - Risk: {}", context.getSymbol(), riskSignal);
    return riskSignal;
  }

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

  @Override
  @Cacheable(value = "strategyParameters", key = "#coinPair")
  public StrategyParameters getStrategyParameters(String coinPair) {
    var validCoinName = getValidCoinName(coinPair);
    return strategyParametersService.getStrategyParameters(validCoinName)
        .orElseGet(this::getStrategyParameters);
  }
}
