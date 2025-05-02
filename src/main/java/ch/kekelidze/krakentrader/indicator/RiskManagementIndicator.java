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
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    var currentPrice = calculateDynamicStopLossPrice(data, params);
    var stopLossTakeProfit = shouldStopLoss(entryPrice, currentPrice, params.lossPercent())
        || shouldTakeProfit(entryPrice, currentPrice, params.profitPercent());
    log.debug(
        "Dynamic price: {}, Entry price: {} | Should stop loss/take profit: {} | Closing time: {}",
        currentPrice, entryPrice, stopLossTakeProfit, data.getLast().getEndTime());
    return stopLossTakeProfit;
  }

  private double calculateDynamicStopLossPrice(List<Bar> candles, StrategyParameters params) {
    double atr = atrAnalyser.calculateATR(candles, params.atrPeriod());
    double currentPrice = candles.getLast().getClosePrice().doubleValue();
    return currentPrice - (params.highVolatilityThreshold() * atr);
  }

  private boolean shouldStopLoss(double entryPrice, double currentPrice, double lossPercent) {
    return currentPrice <= entryPrice * (1 - lossPercent / 100);
  }

  private boolean shouldTakeProfit(double entryPrice, double currentPrice,
      double profitPercent) {
    return currentPrice >= entryPrice * (1 + profitPercent / 100);
  }
}
