package ch.kekelidze.krakentrader.optimize.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.BuyLowSellHighOptimizer;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.time.ZonedDateTime;
import java.util.List;
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
    log.info("Starting optimization for {} coin pairs", coinPairs.size());

    // Optimize each coin pair sequentially (or could be parallelized)
    for (String coinPair : coinPairs) {
      log.info("Optimizing strategy for: {}", coinPair);

      // Create evaluation context with historical data for this coin
      EvaluationContext context = EvaluationContext.builder()
          .symbol(coinPair).period(period)
          .bars(getBars(coinPair, period, startDate, endDate))
          .build();

      // Optimize strategy for this coin
      StrategyParameters params = optimizer.optimizeParameters(context);
      strategyParametersService.saveStrategyParameters(coinPair, "buyLowSellHighStrategy", params);
      log.info("Optimization completed for {}. Best fit: {}", coinPair, params.toString());
    }
  }

  private List<Bar> getBars(String symbol, int period, ZonedDateTime startDate,
      ZonedDateTime endDate) {
    return historicalDataService.queryHistoricalData(List.of(symbol), period).get(symbol).stream()
        .filter(bar -> bar.getEndTime().isAfter(startDate) && bar.getEndTime().isBefore(endDate))
        .toList();
  }
}