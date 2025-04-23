package ch.kekelidze.krakentrader.strategy;

import static ch.kekelidze.krakentrader.trade.util.CoinNameUtils.getValidCoinName;

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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
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

  private final Map<String, StrategyParameters> strategyParametersMap;

  public BuyLowSellHighStrategy(MovingAverageIndicator movingAverageIndicator,
      RsiRangeIndicator rsiIndicator, RiskManagementIndicator riskManagementIndicator,
      VolatilityIndicator volatilityIndicator, SimpleMovingAverageDivergenceIndicator macdIndicator,
      VolumeIndicator volumeIndicator, MovingTrendIndicator movingTrendIndicator) {
    this.movingAverageIndicator = movingAverageIndicator;
    this.rsiIndicator = rsiIndicator;
    this.riskManagementIndicator = riskManagementIndicator;
    this.volatilityIndicator = volatilityIndicator;
    this.macdIndicator = macdIndicator;
    this.volumeIndicator = volumeIndicator;
    this.movingTrendIndicator = movingTrendIndicator;
    this.strategyParametersMap = Map.of(
        "ETH/USD", getETHStrategyParameters(),
        "XRP/USD", getXRPStrategyParameters()
    );
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

  //TODO Q3 ETH
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

  /**
   * Retrieves the strategy parameters specifically configured for Ethereum-based trading (Q4 2024).
   * These parameters define the rules and thresholds for various technical indicators
   * such as moving averages, RSI, MACD, ATR, and volume metrics, which are used to analyze
   * market data for Ethereum trading decisions.
   *
   * @return a {@code StrategyParameters} instance containing predefined indicator
   *         configuration values tailored for Ethereum trading.
   */
  private StrategyParameters getETHStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(21).movingAverageBuyLongPeriod(75)
        .rsiBuyThreshold(48).rsiSellThreshold(79).rsiPeriod(17).lookbackPeriod(6)
        .macdFastPeriod(11).macdSlowPeriod(25).macdSignalPeriod(7)
        .atrPeriod(15).lowVolatilityThreshold(0.87).highVolatilityThreshold(1.3)
        .volumePeriod(27).aboveAverageThreshold(23)
        .lossPercent(3.1).profitPercent(8.9)
        .contractionThreshold(3.4)
        .minimumCandles(225)
        .build();
  }

  /**
   * Retrieves the strategy parameters specifically tailored for XRP trading (optimized Q4 2024).
   * <p>
   * The parameters include configurations for various trading indicators such as moving averages,
   * RSI, MACD, volume analysis, ATR, and volatility thresholds. These parameters are used to guide
   * decision-making in the XRP trading strategy.
   *
   * @return an instance of {@code StrategyParameters} containing predefined settings for XRP
   * trading strategy
   */
  private StrategyParameters getXRPStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(24).movingAverageBuyLongPeriod(50)
        .rsiPeriod(12).rsiBuyThreshold(43).rsiSellThreshold(78)
        .macdFastPeriod(9).macdSlowPeriod(31).macdSignalPeriod(7)
        .volumePeriod(18).aboveAverageThreshold(18)
        .lossPercent(4.8).profitPercent(11.8)
        .contractionThreshold(4.5)
        .atrPeriod(16).lookbackPeriod(7)
        .lowVolatilityThreshold(0.73).highVolatilityThreshold(1.6)
        .minimumCandles(150)
        .build();
  }

  @Override
  public StrategyParameters getStrategyParameters(String coinPair) {
    var validCoinName = getValidCoinName(coinPair);
    if (strategyParametersMap.containsKey(validCoinName)) {
      return strategyParametersMap.get(validCoinName);
    }
    return getStrategyParameters();
  }
}
