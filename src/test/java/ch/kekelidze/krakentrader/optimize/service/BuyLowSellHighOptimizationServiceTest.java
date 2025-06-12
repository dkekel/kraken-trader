package ch.kekelidze.krakentrader.optimize.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.BuyLowSellHighOptimizer;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

@ExtendWith(MockitoExtension.class)
public class BuyLowSellHighOptimizationServiceTest {

  @Mock
  private BuyLowSellHighOptimizer optimizer;

  @Mock
  private HistoricalDataService historicalDataService;

  @Mock
  private StrategyParametersService strategyParametersService;

  private BuyLowSellHighOptimizationService service;

  @BeforeEach
  void setUp() {
    service = new BuyLowSellHighOptimizationService(optimizer, historicalDataService, strategyParametersService);
  }

  @Test
  void testParallelOptimization() throws InterruptedException {
    // Prepare test data
    List<String> coinPairs = List.of("BTC/USD", "ETH/USD", "XRP/USD", "LTC/USD");
    int period = 1;
    ZonedDateTime startDate = ZonedDateTime.now().minusDays(30);
    ZonedDateTime endDate = ZonedDateTime.now();

    // Mock historical data service
    Map<String, List<Bar>> historicalData = new HashMap<>();
    for (String coinPair : coinPairs) {
      historicalData.put(coinPair, createMockBars(100));
    }
    when(historicalDataService.queryHistoricalData(anyList(), anyInt())).thenReturn(historicalData);

    // Create a CountDownLatch to track concurrent executions
    CountDownLatch latch = new CountDownLatch(coinPairs.size());

    // Mock optimizer to simulate work and track concurrent executions
    when(optimizer.optimizeParameters(any(EvaluationContext.class))).thenAnswer(invocation -> {
      // Simulate some work
      Thread.sleep(100);
      // Count down the latch to indicate this task is running
      latch.countDown();
      // Wait a bit to ensure multiple tasks are running concurrently
      Thread.sleep(200);
      return StrategyParameters.builder().build();
    });

    // Run the optimization
    service.optimizeCoinPairs(coinPairs, period, startDate, endDate);

    // Verify that all optimizations were started concurrently
    boolean allTasksStartedConcurrently = latch.await(500, TimeUnit.MILLISECONDS);
    assert allTasksStartedConcurrently : "Not all optimization tasks started concurrently";

    // Verify that the optimizer was called for each coin pair
    verify(optimizer, times(coinPairs.size())).optimizeParameters(any(EvaluationContext.class));

    // Verify that the parameters were saved for each coin pair
    for (String coinPair : coinPairs) {
      verify(strategyParametersService).saveStrategyParameters(
          eq(coinPair), eq("buyLowSellHighStrategy"), any(StrategyParameters.class));
    }
  }

  private List<Bar> createMockBars(int count) {
    List<Bar> bars = new ArrayList<>();
    ZonedDateTime time = ZonedDateTime.now().minusDays(count);
    for (int i = 0; i < count; i++) {
      bars.add(BaseBar.builder()
          .timePeriod(Duration.ofDays(1))
          .endTime(time.plusDays(i))
          .openPrice(DecimalNum.valueOf(100.0))
          .highPrice(DecimalNum.valueOf(105.0))
          .lowPrice(DecimalNum.valueOf(95.0))
          .closePrice(DecimalNum.valueOf(102.0))
          .volume(DecimalNum.valueOf(1000.0))
          .build());
    }
    return bars;
  }
}
