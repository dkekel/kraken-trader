package ch.kekelidze.krakentrader.backtester.service;

import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
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

  private final StrategySelector strategySelector;

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
    double totalProfit = 0;
    int wins = 0;
    int trades = 0;
    double entryPrice = 0;
    double positionSize = 0;

    // For drawdown calculation
    List<Double> equityCurve = new ArrayList<>();
    equityCurve.add(currentCapital);

    // Store all trade profits for volatility calculation
    List<Double> tradeReturns = new ArrayList<>();

    var data = context.getBars();
    for (int i = params.minimumCandles(); i < data.size(); i++) {
      // Calculate indicators
      List<Bar> sublist = data.subList(i - params.minimumCandles(), i);

      // Current price for equity calculation
      double currentPrice = data.get(i).getClosePrice().doubleValue();

      var evaluationContext = EvaluationContext.builder().symbol(context.getSymbol()).bars(sublist)
          .build();
      // Execute strategy logic
      if (!inPosition && strategy.shouldBuy(evaluationContext, params)) {
        trades++;
        entryPrice = currentPrice;
        inPosition = true;
        // For simplicity, assume we use 100% of capital
        positionSize = currentCapital / entryPrice;
        log.debug("BUY at: {} on {}", entryPrice, data.get(i).getEndTime());
      } else if (inPosition && strategy.shouldSell(evaluationContext, entryPrice, params)) {
        trades++;
        double profit = (currentPrice - entryPrice) / entryPrice * 100;
        if (profit > 0) {
          wins++;
        }

        // Add profit to list of returns
        tradeReturns.add(profit);

        // Update capital
        currentCapital = positionSize * currentPrice;
        inPosition = false;
        totalProfit += profit;

        log.debug("SELL at: {} on {} | Profit: {}%", currentPrice, data.get(i).getEndTime(),
            profit);
      }

      // Update equity curve for each bar (whether in position or not)
      if (inPosition) {
        // If in position, equity is affected by current market price
        double currentEquity = positionSize * currentPrice;
        equityCurve.add(currentEquity);
      } else {
        // If not in position, equity remains the same as current capital
        equityCurve.add(currentCapital);
      }

    }

    // Calculate volatility as standard deviation
    double volatility = calculateStandardDeviation(tradeReturns);

    // Calculate maximum drawdown from equity curve
    double maxDrawdown = calculateMaxDrawdown(equityCurve);

    // Calculate metrics
    return BacktestResult.builder()
        .totalProfit(totalProfit)
        .sharpeRatio(calculateSharpe(totalProfit, volatility, tradeReturns.size(), data.size()))
        .winRate(trades > 0 ? wins / (double) trades : 0)
        .maxDrawdown(maxDrawdown)
        .capital(currentCapital)
        .build();
  }

  private double calculateStandardDeviation(List<Double> returns) {
    if (returns.isEmpty()) {
      return 0;
    }

    // Calculate mean
    double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);

    // Calculate sum of squared differences
    double sumSquaredDiff = returns.stream()
        .mapToDouble(r -> Math.pow(r - mean, 2))
        .sum();

    // Calculate standard deviation
    return Math.sqrt(sumSquaredDiff / returns.size());
  }

  private double calculateSharpe(double totalProfit, double volatility, int numTrades,
      int totalPeriods) {
    if (volatility == 0 || numTrades == 0) {
      return 0;
    }

    // Calculate the number of years in the backtest period
    double yearsInPeriod = totalPeriods / (252.0 * 24.0); // assuming 1-hour bars

    // Annualize the return
    double annualizedReturn = Math.pow(1 + totalProfit / 100.0, 1.0 / yearsInPeriod) - 1;

    // Annualize the volatility
    double annualizedVolatility = volatility * Math.sqrt(252.0 * 24.0 / totalPeriods);

    // Risk-free rate (could be parameterized)
    // Assuming 1.25%
    double riskFreeRate = 0.0125; // Convert percentage to decimal

    // Calculate annualized Sharpe ratio
    return (annualizedReturn - riskFreeRate) / annualizedVolatility;
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
