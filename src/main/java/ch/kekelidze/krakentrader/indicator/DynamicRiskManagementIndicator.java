package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
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
public class DynamicRiskManagementIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;
  private final TradingApiService krakenApiService;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    return true;
  }

  @Override
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    var stopLossCurrentPrice = calculateDynamicStopLossPrice(data, atr, params);
    var takeProfitCurrentPrice = calculateDynamicTakeProfitPrice(data, atr, params);
    var roundTripFeePercentage = calculateFeePercentage(context.getSymbol());
    double adjustedLossPercent = params.lossPercent() - roundTripFeePercentage;
    double adjustedProfitPercent = params.profitPercent() + roundTripFeePercentage;
    var stopLossTakeProfit = shouldStopLoss(entryPrice, stopLossCurrentPrice, adjustedLossPercent)
        || trajectoryBasedTakeProfit(data, entryPrice, takeProfitCurrentPrice,
        adjustedProfitPercent, atr, params);
    log.debug(
        "Dynamic price: {}, Entry price: {} | Should stop loss/take profit: {} | Closing time: {}",
        stopLossCurrentPrice, entryPrice, stopLossTakeProfit, data.getLast().getEndTime());
    return stopLossTakeProfit;
  }

  private double calculateFeePercentage(String coinPair) {
    double takerFeeRate = krakenApiService.getCoinTradingFee(coinPair);
    return takerFeeRate * 2;
  }

  private double calculateDynamicStopLossPrice(List<Bar> candles, double atr,
      StrategyParameters params) {
    double currentPrice = candles.getLast().getClosePrice().doubleValue();
    return currentPrice - (params.highVolatilityThreshold() * atr);
  }

  private double calculateDynamicTakeProfitPrice(List<Bar> candles, double atr,
      StrategyParameters params) {
    double currentPrice = candles.getLast().getClosePrice().doubleValue();
    return currentPrice + (params.highVolatilityThreshold() * atr);
  }


  private boolean shouldStopLoss(double entryPrice, double currentPrice, double lossPercent) {
    return currentPrice <= entryPrice * (1 - lossPercent / 100);
  }

  private boolean shouldTakeProfit(double entryPrice, double currentPrice,
      double profitPercent) {
    return currentPrice >= entryPrice * (1 + profitPercent / 100);
  }

  private boolean trajectoryBasedTakeProfit(List<Bar> bars, double entryPrice, double currentPrice,
      double baseProfitPercent, double atr, StrategyParameters params) {
    if (bars.size() < 10) {
      return shouldTakeProfit(entryPrice, currentPrice, baseProfitPercent);
    }

    // Calculate short-term vs longer-term momentum
    double shortTermChange = calculatePercentChange(bars, 3); // Last 3 periods
    double longerTermChange = calculatePercentChange(bars, 10); // Last 10 periods

    // Find recent high
    double recentHigh = findHighestPrice(bars, params.lookbackPeriod());
    double pullbackFromHigh = (recentHigh - currentPrice) / recentHigh * 100;

    // Calculate volatility-adjusted momentum
    double normalizedShortTerm = shortTermChange / (atr / currentPrice * 100);

    // Combined adjustment factor
    // If positive momentum, scale it (2.0 is a sensitivity parameter)
    // If negative momentum, reduce target more aggressively
    double momentumAdjustment = normalizedShortTerm > 0 ?
        Math.min(1.0, normalizedShortTerm / 2.0) :
        Math.max(0.5, 0.7 + normalizedShortTerm / 4.0);

    double pullbackAdjustment = Math.max(0.7, 1.0 - (pullbackFromHigh / 10.0));

    // Aggregate adjustments - more sophisticated weighting possible
    double adjustmentFactor = Math.min(1.0, (momentumAdjustment * 0.6) + (pullbackAdjustment * 0.4));
    adjustmentFactor = Math.max(0.5, adjustmentFactor); // Set a floor

    double adjustedProfitPercent = baseProfitPercent * adjustmentFactor;

    log.debug("Short-term momentum: {}%, Long-term: {}%, Pullback: {}%, Adjustment: {}, Target: {}%",
        shortTermChange, longerTermChange, pullbackFromHigh, adjustmentFactor, adjustedProfitPercent);

    return shouldTakeProfit(entryPrice, currentPrice, adjustedProfitPercent);
  }

  // Helper methods
  private double calculatePercentChange(List<Bar> bars, int periods) {
    if (bars.size() <= periods) return 0;

    double currentPrice = bars.getLast().getClosePrice().doubleValue();
    double pastPrice = bars.get(bars.size() - 1 - periods).getClosePrice().doubleValue();

    return (currentPrice - pastPrice) / pastPrice * 100;
  }

  private double findHighestPrice(List<Bar> bars, int lookbackPeriods) {
    double highestPrice = 0;
    int periods = Math.min(bars.size(), lookbackPeriods);

    for (int i = 1; i <= periods; i++) {
      double high = bars.get(bars.size() - i).getHighPrice().doubleValue();
      if (high > highestPrice) highestPrice = high;
    }

    return highestPrice;
  }
}
