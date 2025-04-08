package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

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

  private final MovingAverageIndicator movingAverageIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final RsiIndicator rsiIndicator;

  /**
   * Determines whether a buy signal is present based on the provided price data
   * and trading strategy parameters. This method evaluates multiple conditions:
   * - If the data indicates a buy signal based on specified moving average periods.
   * - If the 50-period moving average is below the 100-period moving average.
   * - If the 100-period moving average is below the 200-period moving average.
   *
   * @param data   a list of price bars, representing the market data to analyze
   * @param params the strategy parameters that configure trading rules and thresholds
   * @return true if all the conditions for a buy signal are met; false otherwise
   */
  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    var buySignalParams = StrategyParameters.builder().movingAverageShortPeriod(9)
        .movingAverageLongPeriod(50).build();
    var buySignal = movingAverageIndicator.isBuySignal(data, buySignalParams);
    var ma50below100 = isMa50Below100(data);
    var ma100below200 = isMa100Below200(data);
    log.debug("Is buy signal: {}, MA50 below 100: {}, MA100 below 200: {}", buySignal, ma50below100,
        ma100below200);
    return buySignal && ma50below100 && ma100below200;
  }

  /**
   * Determines whether a sell action should be executed based on multiple trading indicators.
   * The method evaluates sell signals from moving average indicators, risk management indicators,
   * and additional conditions related to moving averages.
   *
   * @param data        the list of price bars containing market data
   * @param entryPrice  the entry price of the trade
   * @param params      the strategy parameters for decision-making
   * @return true if any of the sell signal conditions are met, false otherwise
   */
  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    var sellSignalParams = StrategyParameters.builder().movingAverageShortPeriod(9)
        .movingAverageLongPeriod(26).build();
    var sellSignal = movingAverageIndicator.isSellSignal(data, entryPrice, sellSignalParams);
    var riskSellSignal = riskManagementIndicator.isSellSignal(data, entryPrice, params);
    var rsiSignal = rsiIndicator.isSellSignal(data, entryPrice, params);
    var ma50greaterThan100 = isMa50GreaterThan100(data);
    var ma100greaterThan200 = isMa100GreaterThan200(data);
    log.debug(
        "Is sell signal: {}, Risk sell signal: {}, MA50 greater than 100: {}, MA100 greater than 200: {}",
        sellSignal, riskSellSignal, ma50greaterThan100, ma100greaterThan200);
    return sellSignal || riskSellSignal || rsiSignal || (ma50greaterThan100 && ma100greaterThan200);
  }

  private boolean isMa50Below100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isLessThan(ma100.getValue(endIndex));
  }

  private boolean isMa100Below200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isLessThan(ma200.getValue(endIndex));
  }

  private boolean isMa50GreaterThan100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isGreaterThan(ma100.getValue(endIndex));
  }

  private boolean isMa100GreaterThan200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isGreaterThan(ma200.getValue(endIndex));
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .rsiBuyThreshold(35).rsiSellThreshold(70).rsiPeriod(14)
        .lossPercent(5).profitPercent(15)
        .minimumCandles(150)
        .build();
  }
}
