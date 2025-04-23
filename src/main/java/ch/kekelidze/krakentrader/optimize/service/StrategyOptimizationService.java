package ch.kekelidze.krakentrader.optimize.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.BuyLowSellHighOptimizer;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StrategyOptimizationService {

  private final BuyLowSellHighOptimizer optimizer;
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
          .symbol(coinPair).period(period)
          .bars(historicalDataService.queryHistoricalData(List.of(coinPair), period).get(coinPair))
          .build();

      // Optimize strategy for this coin
      StrategyParameters params = optimizer.optimizeParameters(context);
      strategyParametersService.saveStrategyParameters(coinPair, params);
      log.info("Optimization completed for {}. Best fit: {}", coinPair, params.toString());
    }
  }
}