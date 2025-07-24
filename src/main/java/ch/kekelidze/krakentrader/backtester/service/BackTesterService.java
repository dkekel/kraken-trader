package ch.kekelidze.krakentrader.backtester.service;

import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.backtester.util.TimeFrameAdjustmentUtils;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.util.TradingCircuitBreaker;
import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackTesterService {

  private static final int PERIODS_PER_YEAR = 365 * 24;

  private final StrategySelector strategySelector;
  private final AtrAnalyser atrAnalyser;
  private final TradingCircuitBreaker circuitBreaker;

  @Value("${trading.cooldown.minutes:15}")
  private int tradeCooldownMinutes;

  @Value("${backtesting.trading-fee:0.0026}")
  private double simulatedTradingFee;

  @PostConstruct
  public void logConfiguration() {
    log.info("BackTester Configuration:");
    log.info("  - Trade cooldown: {} minutes", tradeCooldownMinutes);
    log.info("  - Simulated trading fee: {}%", simulatedTradingFee * 100);
    log.info("  - Circuit breaker protection enabled");
  }

  public BacktestResult runSimulation(EvaluationContext context, double initialCapital) {
    var coin = context.getSymbol();
    Strategy strategy = strategySelector.getBestStrategyForCoin(coin);
    return runSimulation(context, strategy, strategy.getStrategyParameters(coin), initialCapital);
  }

  public BacktestResult runSimulation(EvaluationContext context,
      StrategyParameters strategyParameters, double initialCapital) {
    Strategy strategy = strategySelector.getBestStrategyForCoin(context.getSymbol());
    return runSimulation(context, strategy, strategyParameters, initialCapital);
  }

  public BacktestResult runSimulation(EvaluationContext context, String strategyName,
      StrategyParameters strategyParameters, double initialCapital) {
    Strategy strategy = strategySelector.getStrategy(strategyName);
    return runSimulation(context, strategy, strategyParameters, initialCapital);
  }

  private BacktestResult runSimulation(EvaluationContext context, Strategy strategy,
      StrategyParameters params, double initialCapital) {
    // Simulate trades using parameters
    boolean inPosition = false;
    double currentCapital = initialCapital;
    int wins = 0;
    int trades = 0;
    double entryPrice = 0;
    double positionSize = 0;
    long lastTradeTimestamp = 0;

    String coinPair = context.getSymbol();

    // For drawdown calculation
    List<Double> equityCurve = new ArrayList<>();
    log.debug("{} optimized parameters: {}", coinPair, params);
    var adjustedParameters = TimeFrameAdjustmentUtils.adjustTimeFrame(params, context.getPeriod());
    log.debug("{} adjusted parameters: {}", coinPair, adjustedParameters);

    var data = context.getBars();
    var minBars = adjustedParameters.minimumCandles();
    for (int i = minBars; i < data.size(); i++) {
      long currentSimulatedTime = data.get(i).getEndTime().toInstant().toEpochMilli();
      if (!circuitBreaker.canTrade(coinPair)) {
        var circuitState = circuitBreaker.getDetailedState(coinPair);
        log.debug(
            "Trading halted for {} - Circuit breaker {} | Consecutive losses: {} | Total loss: {}%",
            coinPair,
            circuitState.getState(),
            circuitState.getConsecutiveLosses(),
            String.format("%.2f", circuitState.getTotalLossPercent()));

        // Update equity curve even when not trading
        updateEquityCurve(equityCurve, currentCapital, inPosition, positionSize, data.get(i));
        continue;
      }

      if (!canTradeBasedOnCooldown(lastTradeTimestamp, currentSimulatedTime)) {
        log.debug("Skipping trade for {} due to cooldown", coinPair);
        updateEquityCurve(equityCurve, currentCapital, inPosition, positionSize, data.get(i));
        continue;
      }

      // Calculate indicators
      List<Bar> sublist = data.subList(i - minBars, i);

      // Current price for equity calculation
      double currentPrice = data.get(i).getClosePrice().doubleValue();

      var evaluationContext = EvaluationContext.builder().symbol(coinPair).bars(sublist)
          .build();

      var buySignal = strategy.shouldBuy(evaluationContext, adjustedParameters);
      var sellSignal = false;
      if (inPosition) {
        sellSignal = strategy.shouldSell(evaluationContext, entryPrice, adjustedParameters);
      }

      if (buySignal && sellSignal) {
        log.debug(
            "Conflicting BUY and SELL signals detected for {} - skipping trade to avoid whipsaw",
            coinPair);
        updateEquityCurve(equityCurve, currentCapital, inPosition, positionSize, data.get(i));
        continue;
      }

      // Execute strategy logic
      if (!inPosition && buySignal) {
        trades++;
        entryPrice = currentPrice;
        inPosition = true;
        positionSize = calculateAdaptivePositionSize(coinPair, sublist, entryPrice, currentCapital,
            adjustedParameters);
        currentCapital -= positionSize * entryPrice;
        log.debug("BUY {} at: {} on {}", positionSize, entryPrice, data.get(i).getEndTime());
      } else if (inPosition && (sellSignal || i == data.size() - 1)) {
        trades++;
        inPosition = false;
        double profit = (currentPrice - entryPrice) / entryPrice * 100;
        currentCapital += positionSize * currentPrice;
        if (profit > 0) {
          wins++;
        }
        log.debug("SELL at: {} on {} | Profit: {}%", currentPrice, data.get(i).getEndTime(),
            profit);
      }

      // Update equity curve for each bar (whether in position or not)
      if (inPosition) {
        // If in position, equity is affected by current market price
        double currentEquity = currentCapital + positionSize * currentPrice;
        equityCurve.add(currentEquity);
      } else {
        // If not in position, equity remains the same as current capital
        equityCurve.add(currentCapital);
      }
    }

    // Calculate maximum drawdown from equity curve
    double maxDrawdown = calculateMaxDrawdown(equityCurve);

    var periodicReturns = calculatePeriodicReturns(equityCurve);
    double sharpeRatio = calculateSharpe(periodicReturns);

    // Calculate metrics
    return BacktestResult.builder()
        .totalProfit(getMeanReturn(periodicReturns) * PERIODS_PER_YEAR * 100)
        .totalTrades(trades)
        .sharpeRatio(sharpeRatio)
        .winRate(trades > 0 ? wins / (double) trades : 0)
        .maxDrawdown(maxDrawdown)
        .capital(currentCapital)
        .build();
  }

  /**
   * Simulates cooldown checking - aligned with TradeService logic
   */
  private boolean canTradeBasedOnCooldown(long lastTradeTimestamp, long currentTimestamp) {
    if (lastTradeTimestamp == 0) {
      return true;
    }

    long tradeCooldownMs = tradeCooldownMinutes * 60L * 1000L;
    long timeSinceLastTrade = currentTimestamp - lastTradeTimestamp;
    return timeSinceLastTrade >= tradeCooldownMs;
  }

  /**
   * Updates equity curve - handles both in-position and out-of-position scenarios
   */
  private void updateEquityCurve(List<Double> equityCurve, double currentCapital,
      boolean inPosition, double positionSize, Bar currentBar) {
    if (inPosition) {
      // If in position, equity is affected by current market price
      double currentPrice = currentBar.getClosePrice().doubleValue();
      double currentEquity = currentCapital + positionSize * currentPrice;
      equityCurve.add(currentEquity);
    } else {
      // If not in position, equity remains the same as current capital
      equityCurve.add(currentCapital);
    }
  }

  /**
   * Calculates position size as a percentage of capital based on market volatility
   *
   * @param data             Recent price bars
   * @param availableCapital Available capital for position
   * @param params           Strategy parameters
   * @return Recommended position size as percentage of capital
   */
  private double calculateAdaptivePositionSize(String coinPair, List<Bar> data, double entryPrice,
      double availableCapital, StrategyParameters params) {
    // Calculate ATR as percentage of price
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    double atrPercent = (atr / currentPrice) * 100;
    var lowerBound = 2.0;
    var upperBound = 12.0;

    // Base position size (percentage of capital)
    double basePositionSize = 0.5; // Default 60% of capital

    // Reduce position size if circuit breaker is in test mode
    var circuitState = circuitBreaker.getCircuitState(coinPair);
    double circuitAdjustment =
        (circuitState == TradingCircuitBreaker.CircuitState.HALF_OPEN) ? 0.5 : 1.0;

    double capitalPercentage;
    // Adjust position size based on volatility
    if (atrPercent < lowerBound) {
      // Low volatility - can take larger position
      capitalPercentage = Math.min(basePositionSize * 1.5, 1.0);
    } else if (atrPercent > upperBound) {
      // High volatility - reduce position size
      capitalPercentage = basePositionSize * 0.5;
    } else {
      // Normal volatility - use base size
      capitalPercentage = basePositionSize;
    }

    // Apply circuit breaker adjustment
    capitalPercentage *= circuitAdjustment;

    // Account for round-trip fees - using simulated fee
    double roundTripFeeImpact = simulatedTradingFee * 2; // Both buy and sell
    double feeAdjustedCapitalPercentage = capitalPercentage * (1 - roundTripFeeImpact);

    return availableCapital * feeAdjustedCapitalPercentage / entryPrice;
  }

  /**
   * Calculates periodic returns from an equity curve
   *
   * @param equityCurve List of equity values at each time point
   * @return List of periodic returns
   */
  private List<Double> calculatePeriodicReturns(List<Double> equityCurve) {
    List<Double> returns = new ArrayList<>();

    // Skip the first entry as we need previous value to calculate return
    for (int i = 1; i < equityCurve.size(); i++) {
      // Simple return calculation: (current - previous) / previous
      double previousEquity = equityCurve.get(i - 1);
      double currentEquity = equityCurve.get(i);

      if (previousEquity != 0) {
        double periodicReturn = (currentEquity - previousEquity) / previousEquity;
        returns.add(periodicReturn);
      }
    }

    return returns;
  }

  /**
   * Calculates Sharpe ratio based on periodic returns
   *
   * @param periodicReturns List of returns for each period
   * @return Sharpe ratio
   */
  private double calculateSharpe(List<Double> periodicReturns) {
    double meanReturn = getMeanReturn(periodicReturns);

    // Calculate standard deviation of returns
    double sumSquaredDiff = 0.0;
    for (Double ret : periodicReturns) {
      double diff = ret - meanReturn;
      sumSquaredDiff += diff * diff;
    }
    double stdDev = Math.sqrt(sumSquaredDiff / periodicReturns.size());

    // Avoid division by zero
    if (stdDev == 0) {
      return 0.0;
    }

    double annualizedMean = meanReturn * PERIODS_PER_YEAR;
    double annualizedStdDev = stdDev * Math.sqrt(PERIODS_PER_YEAR);

    // Add risk-free rate if you want (classic Sharpe ratio)
    double riskFreeRate = 0.02;
    double annualizedExcessReturn = annualizedMean - riskFreeRate;

    return annualizedExcessReturn / annualizedStdDev;
  }

  private static double getMeanReturn(List<Double> periodicReturns) {
    if (periodicReturns.isEmpty()) {
      return 0.0;
    }

    // Calculate mean return
    double sumReturns = 0.0;
    for (Double ret : periodicReturns) {
      sumReturns += ret;
    }
    return sumReturns / periodicReturns.size();
  }

  private double calculateMaxDrawdown(List<Double> equityCurve) {
    if (equityCurve.isEmpty()) {
      return 0;
    }

    double maxDrawdown = 0;
    double peak = equityCurve.getFirst();

    for (double value : equityCurve) {
      if (value > peak) {
        peak = value; // New peak
      } else {
        // Calculate current drawdown
        double currentDrawdown = (peak - value) / peak * 100;
        if (currentDrawdown > maxDrawdown) {
          maxDrawdown = currentDrawdown;
        }
      }
    }

    return maxDrawdown;
  }

}
