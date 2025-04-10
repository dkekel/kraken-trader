package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.time.ZonedDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
 * <p>
 * Trade result 2024-2025: BacktestResult[totalProfit=16.204985678128413,
 * sharpeRatio=-0.08510771986872004, maxDrawdown=10.560888552893571, winRate=0.25,
 * capital=11631.808934383058]
 */
@Slf4j
@Component("multiTimeFrameLowHigh")
@RequiredArgsConstructor
public class MultiTimeFrameLowHighStrategy implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final HistoricalDataService historicalDataService;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var rsiSignal = rsiIndicator.isBuySignal(data, params);
    var maSignal = calculateMovingAverage(context.getSymbol(), data.getLast().getEndTime(), params);
    var endIndex = maSignal.endIndex();
    var maBuySignal = maSignal.maShort().getValue(endIndex)
        .isLessThan(data.getLast().getClosePrice());
    log.debug("RSI buy signal: {}, MA buy signal: {}", rsiSignal, maBuySignal);
    return rsiSignal && maBuySignal;
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    var rsiSignal = rsiIndicator.isSellSignal(data, entryPrice, params);
    var maSignal = calculateMovingAverage(context.getSymbol(), data.getLast().getEndTime(), params);
    var endIndex = maSignal.endIndex();
    var riskManagementSignal = riskManagementIndicator.isSellSignal(data, entryPrice, params);
    var maSellSignal = maSignal.maShort().getValue(endIndex)
        .isGreaterThan(data.getLast().getClosePrice());
    log.debug("RSI sell signal: {}, MA sell signal: {}, Risk sell signal: {}", rsiSignal,
        maSellSignal, riskManagementSignal);
    return rsiSignal && maSellSignal || riskManagementSignal;
  }

  private MovingAverageIndicator.MovingAverage calculateMovingAverage(String symbol,
      ZonedDateTime closingTimestamp, StrategyParameters params) {
    var shortPeriodData = historicalDataService.queryHistoricalData(List.of(symbol), 15).get(symbol)
        .stream().filter(bar -> bar.getEndTime().isBefore(closingTimestamp) || bar.getEndTime()
            .isEqual(closingTimestamp)).toList();
    var shortCandles = shortPeriodData.subList(
        Math.max(0, shortPeriodData.size() - params.movingAverageShortPeriod() * 3),
        shortPeriodData.size());
    return movingAverageIndicator.calculateMovingAverage(shortCandles, params);
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

  @Override
  public int getPeriod() {
    return 4 * 60;
  }
}
