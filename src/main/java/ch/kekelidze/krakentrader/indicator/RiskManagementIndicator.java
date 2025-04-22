package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskManagementIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    return true;
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var currentPrice = calculateDynamicStopLossPrice(data, params);
    var stopLossTakeProfit = shouldStopLoss(entryPrice, currentPrice, params.lossPercent())
        || shouldTakeProfit(entryPrice, currentPrice, params.profitPercent());
    log.debug("Shot stop loss/take profit: {} | Closing time: {}", stopLossTakeProfit,
        data.getLast().getEndTime());
    return stopLossTakeProfit;
  }

  private double calculateDynamicStopLossPrice(List<Bar> candles, StrategyParameters params) {
    double atr = atrAnalyser.calculateATR(candles, params.atrPeriod());
    double currentPrice = candles.getLast().getClosePrice().doubleValue();
    return currentPrice - (params.highVolatilityThreshold() * atr);
  }

  public boolean shouldStopLoss(double entryPrice, double currentPrice, double lossPercent) {
    return currentPrice <= entryPrice * (1 - lossPercent / 100);
  }

  public boolean shouldTakeProfit(double entryPrice, double currentPrice,
      double profitPercent) {
    return currentPrice >= entryPrice * (1 + profitPercent / 100);
  }

  /**
   * Calculates a take profit level based on the current price range
   */
  public double calculateRangeBasedTakeProfit(EvaluationContext context, double entryPrice) {
    // Get recent highs and lows to determine range
    List<Bar> bars = context.getBars();
    int lookbackPeriod = 50; // Look back 50 bars (roughly two days on 1h candles)

    // Find high and low prices in the lookback period
    double recentHigh = Double.MIN_VALUE;
    double recentLow = Double.MAX_VALUE;

    int barsToAnalyze = Math.min(lookbackPeriod, bars.size());
    List<Bar> recentBars = bars.subList(bars.size() - barsToAnalyze, bars.size());

    for (Bar bar : recentBars) {
      var high = bar.getHighPrice().doubleValue();
      var low = bar.getLowPrice().doubleValue();
      if (high > recentHigh) {
        recentHigh = high;
      }
      if (low < recentLow) {
        recentLow = low;
      }
    }

    // Calculate range
    double range = recentHigh - recentLow;

    // Target 40-60% of the range for take profit in ranging market
    return entryPrice + (range * 0.5);
  }

  /**
   * Calculates dynamic stop loss that tightens as profit increases Returns true if current price is
   * below the calculated dynamic stop level
   */
  public boolean calculateDynamicStopLoss(double entryPrice, double currentPrice) {
    // Calculate current profit percentage
    double profitPercent = (currentPrice - entryPrice) / entryPrice * 100.0;

    // Set the stop loss level based on current profit
    double stopLevel;

    // The higher the profit, the tighter the stop - but we check conditions in proper order
    if (profitPercent > 8.0) {
      // Once we're up 8%, lock in 5% profit
      stopLevel = entryPrice * 1.05;
    } else if (profitPercent > 5.0) {
      // Once we're up 5%, lock in 2% profit
      stopLevel = entryPrice * 1.02;
    } else if (profitPercent > 3.0) {
      // Once we're up 3%, move stop to break-even
      stopLevel = entryPrice;
    } else {
      // Not enough profit to activate dynamic stop
      return false;
    }

    // Return true if price has fallen below our dynamic stop level
    return currentPrice < stopLevel;
  }

  public boolean shouldTrailingStopLoss(double entryPrice, double highestSinceEntry,
      double currentPrice, double trailPercent) {
    // Once price has moved in your favor by trailPercent, activate trailing stop
    double activationLevel = entryPrice * (1 + trailPercent / 100);

    // If price hasn't reached activation level, no trailing stop
    if (highestSinceEntry < activationLevel) {
      return false;
    }

    // Trail by trailPercent from the highest price reached
    double trailingStopLevel = highestSinceEntry * (1 - trailPercent / 100);

    return currentPrice <= trailingStopLevel;
  }
}
