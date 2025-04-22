package ch.kekelidze.krakentrader.backtester.service;

import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.backtester.util.TimeFrameAdjustmentUtils;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.service.SentimentDataService;
import ch.kekelidze.krakentrader.indicator.settings.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class BackTesterService {

  private static final int PERIODS_PER_YEAR = 365 * 24;

  private final StrategySelector strategySelector;
  private final AtrAnalyser atrAnalyser;
  private final SentimentDataService sentimentDataService;

  public BacktestResult runSimulation(EvaluationContext context, double initialCapital) {
    Strategy strategy = strategySelector.getBestStrategyForCoin(context.getSymbol());
    return runSimulation(context, strategy, strategy.getStrategyParameters(), initialCapital);
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

    // For drawdown calculation
    List<Double> equityCurve = new ArrayList<>();
    var adjustedParameters = TimeFrameAdjustmentUtils.adjustTimeFrame(params, context.getPeriod());
    preloadSentimentData(context);

    var data = context.getBars();
    var minBars = adjustedParameters.minimumCandles();
    for (int i = minBars; i < data.size(); i++) {
      // Calculate indicators
      List<Bar> sublist = data.subList(i - minBars, i);

      // Current price for equity calculation
      double currentPrice = data.get(i).getClosePrice().doubleValue();

      var evaluationContext = EvaluationContext.builder().symbol(context.getSymbol()).bars(sublist)
          .build();
      // Execute strategy logic
      if (!inPosition && strategy.shouldBuy(evaluationContext, adjustedParameters)) {
        trades++;
        entryPrice = currentPrice;
        inPosition = true;
        positionSize = calculateAdaptivePositionSize(context, entryPrice, currentCapital,
            adjustedParameters);
        currentCapital -= positionSize * entryPrice;
        log.debug("BUY {} at: {} on {}", positionSize, entryPrice, data.get(i).getEndTime());
      } else if (inPosition && (strategy.shouldSell(evaluationContext, entryPrice,
          adjustedParameters) || i == data.size() - 1)) {
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
   * Preloads sentiment data for the entire backtest period to avoid API calls during simulation
   */
  private void preloadSentimentData(EvaluationContext context) {
    List<Bar> data = context.getBars();
    if (data.isEmpty()) return;

    // Get time range for the backtest
    long startTime = data.getFirst().getEndTime().toEpochSecond();
    long endTime = data.getLast().getEndTime().toEpochSecond();

    // Calculate interval between 5-min candles in seconds
    long intervalSeconds = 5 * 60;

    log.info("Preloading sentiment data for {} from {} to {}",
        context.getSymbol(),
        Instant.ofEpochSecond(startTime),
        Instant.ofEpochSecond(endTime));

    // Load sentiment data for the entire backtest period
    sentimentDataService.loadHistoricalSentimentData(
        context.getSymbol(), startTime, endTime, intervalSeconds);
  }


  /**
   * Calculates position size as a percentage of capital based on market volatility
   *
   * @param context          with recent price bars
   * @param availableCapital Available capital for position
   * @param params           Strategy parameters
   * @return Recommended position size as percentage of capital
   */
  private double calculateAdaptivePositionSize(EvaluationContext context, double entryPrice,
      double availableCapital, StrategyParameters params) {
    var data = context.getBars();
    // Calculate ATR
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());

    // Risk percentage based on volatility
    double riskPercent;
    double normalizedAtr = atr / entryPrice * 100; // ATR as percentage of price

    if (normalizedAtr < params.lowVolatilityThreshold()) {
      riskPercent = 3.0; // Higher risk in low volatility
    } else if (normalizedAtr > params.highVolatilityThreshold()) {
      riskPercent = 1.0; // Lower risk in high volatility
    } else {
      riskPercent = 2.0; // Default risk
    }

    // Apply sentiment adjustment to risk if available
    if (!data.isEmpty()) {
      Bar lastBar = data.getLast();
      long timestamp = lastBar.getEndTime().toEpochSecond();

      // Get sentiment score for entry point (-100 to +100)
      double sentiment = sentimentDataService.getSentimentScore(context.getSymbol(), timestamp);

      // Adjust risk based on sentiment
      // Higher confidence (more positive sentiment) = higher position size
      double sentimentFactor = 1.0 + (sentiment / 200.0); // Range 0.5 to 1.5
      riskPercent *= sentimentFactor;
    }

    // Calculate risk amount and position size
    double riskAmount = availableCapital * (riskPercent / 100.0);
    double stopLossDistance = atr * 2; // Stop 2 ATR away from entry

    // Ensure we don't risk more than 2% of capital per trade regardless of adjustments
    double maxRiskAmount = availableCapital * 0.02;
    riskAmount = Math.min(riskAmount, maxRiskAmount);

    // Calculate position size in asset units
    double positionSize = riskAmount / stopLossDistance;

    // Ensure position size doesn't exceed available capital
    return Math.min(positionSize, availableCapital / entryPrice);
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
      double periodicReturn = (currentEquity - previousEquity) / previousEquity;

      returns.add(periodicReturn);
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
