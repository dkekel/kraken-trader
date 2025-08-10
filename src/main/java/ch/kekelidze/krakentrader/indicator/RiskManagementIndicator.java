package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class RiskManagementIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;
  private final TradingApiService krakenApiService;

  private final Map<String, TrailingState> trailingStates = new ConcurrentHashMap<>();

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    return true;
  }

  @Override
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    var symbol = context.getSymbol();
    var roundTripFeePercentage = calculateFeePercentage(symbol);

    var currentPrice = calculateDynamicStopLossPrice(data, params);
    double breakevenPrice = entryPrice * (1 + roundTripFeePercentage / 100);

    double adjustedLossPercent = params.lossPercent() - roundTripFeePercentage;
    double adjustedProfitPercent = params.profitPercent() + roundTripFeePercentage;

    if (shouldStopLoss(entryPrice, currentPrice, adjustedLossPercent)) {
      cleanupTrailingState(symbol);
      log.debug("Stop loss triggered at price: {}, Entry: {} | Closing time: {} ",
          currentPrice, entryPrice, data.getLast().getEndTime());
      return true;
    }

    if (shouldTakeProfit(entryPrice, currentPrice, adjustedProfitPercent)) {
      cleanupTrailingState(symbol);
      log.debug("Take profit triggered at price: {}, Entry: {} | Closing time: {}",
          currentPrice, entryPrice, data.getLast().getEndTime());
      return true;
    }

    if (currentPrice > breakevenPrice) {
      boolean shouldSell = shouldTrailingSell(symbol, currentPrice, breakevenPrice, params);
      if (shouldSell) {
        cleanupTrailingState(symbol);
        log.debug("Trailing sell triggered at price: {}, Entry: {}, Breakeven: {}",
            currentPrice, entryPrice, breakevenPrice);
        return true;
      }
    } else {
      // Reset trailing state if we're below breakeven
      cleanupTrailingState(symbol);
    }

    log.debug("No sell signal - Price: {}, Entry: {}, Breakeven: {}, Closing time: {}",
        currentPrice, entryPrice, breakevenPrice, data.getLast().getEndTime());
    return false;

  }

  private double calculateFeePercentage(String coinPair) {
    double takerFeeRate = krakenApiService.getCoinTradingFee(coinPair);
    return takerFeeRate * 2;
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

  private boolean shouldTrailingSell(String symbol, double currentPrice, double breakevenPrice,
      StrategyParameters params) {
    TrailingState state = trailingStates.computeIfAbsent(symbol, k -> new TrailingState());

    // Update peak price if current price is higher
    if (currentPrice > state.peakPrice) {
      state.peakPrice = currentPrice;
      state.consecutiveDeclines = 0;
      state.isTrailingActive = true;
      // Don't sell while the price is still rising
      return false;
    }

    // Price has declined from peak
    if (state.isTrailingActive && currentPrice < state.peakPrice) {
      state.consecutiveDeclines++;

      // Calculate decline percentage from peak
      double declinePercent = ((state.peakPrice - currentPrice) / state.peakPrice) * 100;

      double trailingThreshold = getTrailingThreshold(params);

      // Sell if decline exceeds the threshold and we have consecutive declines
      if (declinePercent >= trailingThreshold && state.consecutiveDeclines >= 2) {
        // Additional safety check: ensure we're still above breakeven
        if (currentPrice > breakevenPrice) {
          log.debug("Trailing sell conditions met - "
                  + "Peak: {}, Current: {}, Decline: {}%, Consecutive declines: {}",
              state.peakPrice, currentPrice, declinePercent, state.consecutiveDeclines);
          return true;
        }
      }
    }

    return false;
  }

  private double getTrailingThreshold(StrategyParameters params) {
    // For now, using a dynamic threshold based on volatility
    // Higher volatility = higher threshold to avoid false signals
    return Math.max(0.5, params.highVolatilityThreshold() * 0.5);
  }

  private void cleanupTrailingState(String symbol) {
    trailingStates.remove(symbol);
  }

  private static class TrailingState {
    double peakPrice = 0.0;
    int consecutiveDeclines = 0;
    boolean isTrailingActive = false;
  }
}
