package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingTrendIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.SimpleMovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.VolatilityIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.analyser.TrendAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

@Slf4j
@Component("buyLowSellHighStrategy")
public class BuyLowSellHighStrategy implements Strategy {

  private final TrendAnalyser trendAnalyser;
  private final RiskManagementIndicator riskManagementIndicator;
  private final VolatilityIndicator volatilityIndicator;
  private final SimpleMovingAverageDivergenceIndicator macdIndicator;
  private final VolumeIndicator volumeIndicator;
  private final MovingTrendIndicator movingTrendIndicator;
  private final StrategyParametersService strategyParametersService;

  public BuyLowSellHighStrategy(TrendAnalyser trendAnalyser,
      RiskManagementIndicator riskManagementIndicator,
      VolatilityIndicator volatilityIndicator, SimpleMovingAverageDivergenceIndicator macdIndicator,
      VolumeIndicator volumeIndicator, MovingTrendIndicator movingTrendIndicator,
      StrategyParametersService strategyParametersService) {
    this.trendAnalyser = trendAnalyser;
    this.riskManagementIndicator = riskManagementIndicator;
    this.volatilityIndicator = volatilityIndicator;
    this.macdIndicator = macdIndicator;
    this.volumeIndicator = volumeIndicator;
    this.movingTrendIndicator = movingTrendIndicator;
    this.strategyParametersService = strategyParametersService;
  }

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    boolean macdConfirmed = macdIndicator.isBuySignal(context, params);
    var data = context.getBars();
    var historicalData = data.subList(0,
        Math.min(data.size(), data.size() - params.lookbackPeriod()));
    boolean wasInDowntrend = trendAnalyser.isDowntrend(historicalData, context.getSymbol(), params);
    boolean bullishSignal = trendAnalyser.isBullishSignal(context, params);
    boolean hasDivergence = trendAnalyser.hasBullishDivergence(context, params);

    boolean movingTrend = movingTrendIndicator.isBuySignal(context, params);

    // Check volatility
    boolean volatilityOK = volatilityIndicator.isBuySignal(context, params);

    boolean trendReversalConfirmed = !trendAnalyser.isDowntrend(data, context.getSymbol(), params);

    // Pre-check if all other signals are strong
    boolean otherSignalsStrong = wasInDowntrend &&
        ((bullishSignal && hasDivergence) ||
            (bullishSignal && macdConfirmed && movingTrend));

    // If other signals are very strong but volatility is the only blocker, do a deeper check
    boolean overrideVolatility = false;
    if (!volatilityOK && otherSignalsStrong) {
      // Call a more detailed volatility check method that considers trend strength
      double currentVolatility = volatilityIndicator.calculateVolatilityPercentage(data, params);
      double maxAcceptableVolatility = params.lowVolatilityThreshold() * 1.5; // Allow 50% higher volatility

      // Check if we're only slightly over the threshold
      if (currentVolatility < maxAcceptableVolatility) {
        log.debug("Volatility override check - Current: {}%, Max acceptable: {}%",
            currentVolatility, maxAcceptableVolatility);
        overrideVolatility = true;
      }
    }

    log.debug("Buy '{}' signals at {} - " +
                    "Volatility: {}{}, MACD: {}, Downtrend: {}, Trend Reversal: {}, Bullish: {}, MovingTrend: {}",
        context.getSymbol(), context.getBars().getLast().getEndTime(),
        volatilityOK, overrideVolatility ? " (overridden)" : "",
        macdConfirmed, wasInDowntrend, trendReversalConfirmed, bullishSignal, movingTrend);


    return wasInDowntrend &&
        (bullishSignal || hasDivergence) &&
        (volatilityOK || overrideVolatility) &&
        macdConfirmed &&
        movingTrend &&
        trendReversalConfirmed;
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();

    // Risk management signal (stop loss/take profit) - this always takes precedence
    boolean riskSignal = riskManagementIndicator.isSellSignal(context, entryPrice, params);

    boolean isInDowntrend = trendAnalyser.isDowntrend(data, context.getSymbol(), params);

    boolean hasBearishPattern = trendAnalyser.hasBearishDivergence(context, params);
    boolean bearishSignal = trendAnalyser.isBearishSignal(context, params);

    // Check for consecutive lower highs or lower lows (a strong bearish pattern)
    boolean hasConsecutiveLowerHighs = trendAnalyser.hasConsecutiveLowerHighsOrLows(data, params);

    // Check for volume spike (often precedes major moves in crypto)
    boolean hasVolumeSurge = volumeIndicator.hasVolumeSurge(data, params);

    var bearishSequence = trendAnalyser.getBearishTrendSequence(context, params);
    boolean hasModerateDowntrend = bearishSequence.isHasModerateDowntrend();
    boolean hasStrongDowntrend = bearishSequence.isHasStrongDowntrend();
    boolean priceConfirmation = bearishSequence.isPriceConfirmation();

    // Log all signals for debugging
    log.debug("Sell '{}' signals at {} - Risk: {}, Downtrend: {}, Moderate: {}, Strong: {}, " +
            "Bearish Pattern: {}, Bearish Signal: {}, Lower Highs: {}, Volume Surge: {}",
        context.getSymbol(), context.getBars().getLast().getEndTime(),
        riskSignal, isInDowntrend, hasModerateDowntrend,
        hasStrongDowntrend, hasBearishPattern, bearishSignal, hasConsecutiveLowerHighs,
        hasVolumeSurge);

    // Tiered decision logic based on downtrend strength:
    boolean technicalSellSignal;

    if (hasStrongDowntrend && priceConfirmation) {
      // For very strong downtrends, minimal confirmation needed
      technicalSellSignal = true;
    } else if (hasModerateDowntrend && priceConfirmation) {
      // For moderate downtrends, require one additional confirmation signal
      technicalSellSignal =
          hasBearishPattern || bearishSignal || hasConsecutiveLowerHighs || hasVolumeSurge;
    } else if (isInDowntrend) {
      // For mild downtrends, require multiple confirmation signals
      int confirmationCount = (hasBearishPattern ? 1 : 0) +
          (bearishSignal ? 1 : 0) +
          (hasConsecutiveLowerHighs ? 1 : 0) +
          (hasVolumeSurge ? 1 : 0);
      technicalSellSignal = confirmationCount >= 2;
    } else {
      // Not in downtrend - need very strong bearish signals to sell
      technicalSellSignal = hasBearishPattern && bearishSignal && hasConsecutiveLowerHighs;
    }

    log.debug("Sell '{}' at {} - Final decision: {}", context.getSymbol(),
        context.getBars().getLast().getEndTime(),
        (riskSignal || technicalSellSignal));

    return riskSignal || technicalSellSignal;
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(20).movingAverageBuyLongPeriod(50)
        .rsiBuyThreshold(45).rsiSellThreshold(70).rsiPeriod(14).lookbackPeriod(5)
        .macdFastPeriod(12).macdSlowPeriod(26).macdSignalPeriod(9)
        .atrPeriod(14).lowVolatilityThreshold(0.9).highVolatilityThreshold(1.5)
        .volumePeriod(24).aboveAverageThreshold(20)
        .lossPercent(3).profitPercent(15)
        .contractionThreshold(3.0)
        .minimumCandles(150)
        .build();
  }

  @Override
  @Cacheable(value = "strategyParameters", key = "#coinPair")
  public StrategyParameters getStrategyParameters(String coinPair) {
    return strategyParametersService.getStrategyParameters(coinPair)
        .orElseGet(this::getStrategyParameters);
  }

  /**
   * Evaluates whether a forced exit should be performed based on risk management signals.
   * The decision is made considering sell signals from the risk management indicator
   * and excludes standard technical indicators like trends or patterns.
   *
   * @param context    the evaluation context containing market data (e.g., symbol, price bars)
   * @param entryPrice the trade's entry price
   * @param params     the strategy parameters used for decision-making, such as stop-loss and
   *                   profit thresholds
   * @return true if a risk-based forced exit signal is generated, false otherwise
   */
  @Override
  public boolean shouldForceExit(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    boolean riskExit = riskManagementIndicator.isSellSignal(context, entryPrice, params);
    if (riskExit) {
      log.debug("[Risk-Only] Forced exit signal for {} at {}", context.getSymbol(),
          context.getBars().getLast().getEndTime());
    }
    return riskExit;

  }
}
