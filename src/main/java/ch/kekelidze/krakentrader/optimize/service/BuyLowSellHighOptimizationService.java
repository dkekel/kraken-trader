package ch.kekelidze.krakentrader.optimize.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.BuyLowSellHighOptimizer;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Slf4j
@Service
@RequiredArgsConstructor
public class BuyLowSellHighOptimizationService {

  private final BuyLowSellHighOptimizer optimizer;
  private final HistoricalDataService historicalDataService;
  private final StrategyParametersService strategyParametersService;

  public void optimizeCoinPairs(List<String> coinPairs, int period, ZonedDateTime startDate,
      ZonedDateTime endDate) {
    log.info("Starting parallel optimization for {} coin pairs", coinPairs.size());

    // Create a thread pool with a reasonable number of threads
    // Using the number of available processors as a baseline
    int numThreads = Math.min(Runtime.getRuntime().availableProcessors(), coinPairs.size());

    try(ExecutorService executor = Executors.newFixedThreadPool(numThreads)) {
      // Create a list of CompletableFuture for each coin pair optimization
      List<CompletableFuture<Void>> futures = coinPairs.stream()
          .map(coinPair -> CompletableFuture.supplyAsync(() -> {
            log.info("Optimizing strategy for: {}", coinPair);

            // Create an evaluation context with historical data for this coin
            EvaluationContext context = EvaluationContext.builder()
                .symbol(coinPair).period(period)
                .bars(getBars(coinPair, period, startDate, endDate))
                .build();

            // Optimize strategy for this coin
            StrategyParameters params = optimizer.optimizeParameters(context);

            // Return the results
            return new OptimizationResult(coinPair, params);
          }, executor)
          .thenAccept(result -> {
            // Save the parameters after optimization is complete
            strategyParametersService.saveStrategyParameters(
                result.coinPair, "buyLowSellHighStrategy", result.parameters);
            log.info("Optimization completed for {}. Best fit: {}", 
                result.coinPair, result.parameters.toString());
          }))
          .toList();

      // Wait for all optimizations to complete
      CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

      log.info("All optimizations completed successfully");
    }
  }

  // Helper class to store optimization results
  private record OptimizationResult( String coinPair, StrategyParameters parameters) {
  }

  private List<Bar> getBars(String symbol, int period, ZonedDateTime startDate,
      ZonedDateTime endDate) {
    return historicalDataService.queryHistoricalData(List.of(symbol), period).get(symbol).stream()
        .filter(bar -> bar.getEndTime().isAfter(startDate) && bar.getEndTime().isBefore(endDate))
        .toList();
  }
}
