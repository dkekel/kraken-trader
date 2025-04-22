package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.analyser.VolatilityAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * <b>Uses 1 hour candles</b>
 * <p>
 * <p>
 * The MovingAverageScalper class implements a trading strategy based on moving averages of
 * different periods and incorporates risk management. It analyzes financial data (candlestick bars)
 * to determine buy and sell signals based on predefined conditions.
 * <p>
 * This strategy checks for convergence and divergence of moving averages (MA) of varying time
 * periods as indicators for market entry and exit points. Additionally, it integrates risk
 * management by evaluating stop-loss or take-profit conditions.
 * <p>
 * The core functionality includes: - Identifying buy opportunities by checking if short-period MAs
 * cross above long-period MAs under specific conditions. - Identifying sell opportunities by
 * checking if short-period MAs cross below long-period MAs or if specific risk management levels
 * are triggered. - Custom-defined moving average comparisons for determining broader market
 * trends.
 * <p>
 * Dependencies: - {@link MovingAverageIndicator}: Handles the computation and analysis of moving
 * averages. - {@link RiskManagementIndicator}: Handles the logic for risk management signals.
 * <p>
 * Implements the {@link Strategy} interface for trading strategies.
 * <p>
 * Thread-safety: This class is not thread-safe.
 */
@Slf4j
@Component("XRPMovingAverageScalper")
@RequiredArgsConstructor
public class MovingAverageScalper implements Strategy {

  private static final int PERIOD = 60;
  private final Map<String, Double> highestPrice = new ConcurrentHashMap<>();

  private final MovingAverageIndicator movingAverageIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final VolatilityAnalyser volatilityAnalyser;
  private final RsiIndicator rsiIndicator;

  /**
   * This method evaluates multiple conditions: - If the data indicates a buy signal based on
   * specified moving average periods. - If the 50-period moving average is below the 100-period
   * moving average. - If the 100-period moving average is below the 200-period moving average.
   *
   * @param context context with the coin symbol and a list of price bars, representing the market
   *                data to analyze
   * @param params  the strategy parameters that configure trading rules and thresholds
   * @return true if all the conditions for a buy signal are met; false otherwise
   */
  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var maSignal = movingAverageIndicator.isBuySignal(context, params);
    var ma50below100 = movingAverageIndicator.isMa50Below100(data);
    var ma100Ma200 = movingAverageIndicator.calculateMovingAverage(data, 100, 200);

    // Check if the market is range-bound
    boolean isRangeBound = volatilityAnalyser.isLowVolatility(context, params) ||
        volatilityAnalyser.isInDefinedRange(context);
    boolean rsiSignal = rsiIndicator.isBuySignal(context, params);

    boolean ma100AndMa200Close = movingAverageIndicator.areMovingAveragesWithinThreshold(ma100Ma200,
        params.highVolatilityThreshold());

    // Different strategies based on market conditions
    boolean buySignal;
    if (isRangeBound) {
      // Counter-trend approach for range-bound market
      buySignal = maSignal && ma50below100 && rsiSignal;
    } else {
      // Trend-following approach for trending market
      buySignal = maSignal && !ma50below100 && ma100AndMa200Close;
    }

    if (buySignal) {
      log.debug("Is buy signal: {}, MA50 below 100: {}, MA100 below 200: {}", maSignal,
          ma50below100, ma100AndMa200Close);
    }
    return buySignal;
  }

  /**
   * The method evaluates sell signals from moving average indicators, risk management indicators,
   * and additional conditions related to moving averages.
   *
   * @param context    context with the coin symbol and a list of price bars, representing the
   *                   market data to analyze
   * @param entryPrice the entry price of the trade
   * @param params     the strategy parameters for decision-making
   * @return true if any of the sell signal conditions are met, false otherwise
   */
  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    var highestSinceEntry = highestPrice.compute(context.getSymbol(), (key, previousHighest) ->
        previousHighest == null ? currentPrice : Math.max(previousHighest, currentPrice));

    // Determine if we're in a range-bound market
    boolean isRangeBound = volatilityAnalyser.isLowVolatility(context, params) ||
        volatilityAnalyser.isInDefinedRange(context);

    // Check risk management indicators (stop loss and take profit)
    boolean isStopLoss = riskManagementIndicator.shouldStopLoss(entryPrice, currentPrice,
        params.lossPercent());
    boolean isTakeProfit = riskManagementIndicator.shouldTakeProfit(entryPrice, currentPrice,
        params.profitPercent());
    boolean trailingStopLoss = riskManagementIndicator.shouldTrailingStopLoss(entryPrice,
        highestSinceEntry, currentPrice, params.lossPercent());

    boolean maSignal = movingAverageIndicator.isSellSignal(data, entryPrice, params);
    boolean rsiSignal = rsiIndicator.isSellSignal(data, entryPrice, params);
    boolean isOverboughtInProfit = rsiSignal && currentPrice > entryPrice;

    // Different exit strategies based on market conditions
    if (isRangeBound) {
      // In range-bound markets:
      // 1. Use tighter take profit (calculated from trading range)
      double rangeBasedTakeProfit = riskManagementIndicator.calculateRangeBasedTakeProfit(context,
          entryPrice);
      boolean isRangeProfit = currentPrice >= rangeBasedTakeProfit;
      boolean sellSignal = isStopLoss || isRangeProfit || trailingStopLoss || isOverboughtInProfit;

      if (sellSignal) {
        log.debug(
            "Sell signal indicators - IsStopLoss: {}, IsRangeProfit: {}, RSI Signal: {}, "
                + "CurrentPrice: {}, EntryPrice: {}",
            isStopLoss, isRangeProfit, rsiSignal, currentPrice, entryPrice);

      }
    } else {
      // In trending markets:
      // 1. Allow more room for profits (use standard take profit)
      // 2. Exit on MA reversal signals
      // 3. Use dynamic stop losses that tighten as profit increases
      boolean isDynamicStop = riskManagementIndicator.calculateDynamicStopLoss(entryPrice,
          currentPrice);
      boolean sellSignal = isStopLoss || isTakeProfit || trailingStopLoss || maSignal || isDynamicStop;

      if (sellSignal) {
        log.debug(
            "Sell signal indicators - IsStopLoss: {}, IsTakeProfit: {}, MA Signal: {}, "
                + "Dynamic Stop: {}",
            isStopLoss, isTakeProfit, maSignal, isDynamicStop);
      }
      return sellSignal;
    }

    return false;
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(4).movingAverageBuyLongPeriod(20)
        .movingAverageSellShortPeriod(7).movingAverageSellLongPeriod(47)
        .rsiBuyThreshold(28).rsiSellThreshold(66).rsiPeriod(8)
        .lossPercent(2).profitPercent(14)
        .atrPeriod(3).atrThreshold(3)
        .highVolatilityThreshold(2)
        .minimumCandles(300)
        .build();
  }

  @Override
  public int getPeriod() {
    return PERIOD;
  }
}
