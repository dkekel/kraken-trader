package ch.kekelidze.krakentrader.optimize.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.MultiStrategyOptimizer;
import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;

import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOptimizationService {

  private final MultiStrategyOptimizer optimizer;
  private final StrategySelector strategySelector;
  private final HistoricalDataService historicalDataService;
  private final StrategyParametersService strategyParametersService;

  // Map to store optimized parameters for each coin pair
  private final Map<String, StrategyParameters> optimizedParameters = new ConcurrentHashMap<>();

  public void optimizeCoinPairs(List<String> coinPairs, int period) {
    log.info("Starting optimization for {} coin pairs", coinPairs.size());

    // Optimize each coin pair sequentially (or could be parallelized)
    for (String coinPair : coinPairs) {
      log.info("Optimizing strategy for: {}", coinPair);

      // Create evaluation context with historical data for this coin
      EvaluationContext context = EvaluationContext.builder()
          .symbol(coinPair)
          .period(period)
          .bars(historicalDataService.queryHistoricalData(List.of(coinPair), period).get(coinPair))
          .build();

      // Optimize strategy for this coin
      StrategyParameters params = optimizer.optimizeParameters(context);

      // Store optimized parameters
      optimizedParameters.put(coinPair, params);

      log.info("Optimization completed for {}", coinPair);
    }

    // Get the best strategies report
    Map<String, String> bestStrategies = optimizer.getBestStrategiesReport();

    // Save the best strategy and parameters for each coin pair to the database
    for (String coinPair : coinPairs) {
      String bestStrategy = bestStrategies.get(coinPair);
      StrategyParameters params = optimizedParameters.get(coinPair);

      if (bestStrategy != null && params != null) {
        strategyParametersService.saveStrategyParameters(coinPair, bestStrategy, params);
        log.info("Saved best strategy '{}' and parameters for {} to database", bestStrategy,
            coinPair);
      }
    }

    // Print summary of best strategies
    log.info("Optimization complete. Best strategies per coin:");
    bestStrategies.forEach((coin, result) ->
        log.info("{}: {}", coin, result));
  }

  public Map<String, String> getBestStrategiesMap() {
    return strategySelector.getBestStrategiesMap();
  }

  public Map<String, StrategyParameters> getOptimizedParametersMap() {
    return new ConcurrentHashMap<>(optimizedParameters);
  }
}
