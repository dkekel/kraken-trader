package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

/**
 * <b>Uses 4 hour candles</b>
 * <p>
 * <p>
 * Represents a multi-timeframe trading strategy that combines multiple technical analysis
 * indicators such as Moving Average, RSI (Relative Strength Index), and Risk Management to
 * determine buy and sell signals.
 * <p>
 * This strategy assesses market conditions across different time frames using moving averages and
 * RSI thresholds to identify potential trade opportunities, while incorporating risk management to
 * make decisions based on stop-loss and take-profit levels.
 * <p>
 * It evaluates Buy and Sell conditions by applying the following rules: - For buying, checks if RSI
 * indicates an oversold condition and the current closing price is above the short-term moving
 * average. - For selling, checks if RSI indicates an overbought condition and the current closing
 * price is below the short-term moving average or risk management signals indicate stop-loss or
 * take-profit conditions are met.
 * <p>
 * The strategy is parameterized using {@code StrategyParameters}, which allows configuring aspects
 * of moving average periods, RSI thresholds, and risk management settings.
 * <p>
 * This class depends on the following indicators: - {@link MovingAverageIndicator} for calculating
 * short-term and long-term moving averages. - {@link RsiIndicator} for analyzing relative strength
 * index based buy and sell signals. - {@link RiskManagementIndicator} for determining trade exits
 * based on risk management rules.
 * <p>
 * Implements: - The {@link Strategy} interface to provide custom buy and sell decision logic.
 */
@Component("multiTimeFrameLowHigh")
@RequiredArgsConstructor
public class MultiTimeFrameLowHighStrategy implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;

  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    var rsiSignal = rsiIndicator.isBuySignal(data, params);
    //TODO MA should be calculated with 15min candles instead
    var maSignal = movingAverageIndicator.calculateMovingAverage(data, params);
    var endIndex = maSignal.endIndex();
    return rsiSignal && maSignal.maShort().getValue(endIndex)
        .isLessThan(data.getLast().getClosePrice());
  }

  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    var rsiSignal = rsiIndicator.isSellSignal(data, entryPrice, params);
    var maSignal = movingAverageIndicator.calculateMovingAverage(data, params);
    var endIndex = maSignal.endIndex();
    return rsiSignal && maSignal.maShort().getValue(endIndex)
        .isGreaterThan(data.getLast().getClosePrice())
        || riskManagementIndicator.isSellSignal(data, entryPrice, params);
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(50).movingAverageLongPeriod(50)
        .rsiBuyThreshold(35).rsiSellThreshold(70).rsiPeriod(14)
        .lossPercent(3).profitPercent(4)
        .minimumCandles(50)
        .build();
  }
}
