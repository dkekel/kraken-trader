package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.analyser.VolatilityAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
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
@Component("movingAverageScalper")
@RequiredArgsConstructor
public class MovingAverageScalper implements Strategy {

  private static final int PERIOD = 60;

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
    var ma100below200 = movingAverageIndicator.isMa100Below200(data);

    var buySignal = maSignal && ma50below100 && ma100below200;
    if (buySignal) {
      log.debug("Is buy signal: {}, MA50 below 100: {}, MA100 below 200: {}", maSignal,
          ma50below100, ma100below200);
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
    var isVolatilityAcceptable = volatilityAnalyser.isVolatilityAcceptable(data, params);
    if (!isVolatilityAcceptable) {
      log.debug("Volatility not acceptable");
      return false;
    }
    var maSignal = movingAverageIndicator.isSellSignal(context, entryPrice, params);
    var riskSellSignal = riskManagementIndicator.isSellSignal(context, entryPrice, params);
    var rsiSignal = rsiIndicator.isSellSignal(context, entryPrice, params);
    var ma50greaterThan100 = movingAverageIndicator.isMa50GreaterThan100(data);
    var ma100greaterThan200 = movingAverageIndicator.isMa100GreaterThan200(data);

    var sellSignal =
        maSignal || riskSellSignal || rsiSignal || (ma50greaterThan100 && ma100greaterThan200);
    if (sellSignal) {
      log.debug(
          "Is sell signal: {}, Risk sell signal: {}, MA50 greater than 100: {}, MA100 greater than 200: {}",
          maSignal, riskSellSignal, ma50greaterThan100, ma100greaterThan200);
    }
    return sellSignal;
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageBuyShortPeriod(9).movingAverageBuyLongPeriod(50)
        .movingAverageSellShortPeriod(9).movingAverageSellLongPeriod(26)
        .rsiBuyThreshold(35).rsiSellThreshold(70).rsiPeriod(14)
        .lossPercent(3).profitPercent(15)
        .atrPeriod(14).atrThreshold(3)
        .minimumCandles(600)
        .build();
  }

  @Override
  public int getPeriod() {
    return PERIOD;
  }
}
