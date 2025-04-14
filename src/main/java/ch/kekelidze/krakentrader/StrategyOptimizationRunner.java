package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.optimize.Optimizer;
import ch.kekelidze.krakentrader.api.file.service.CsvFileService;
import ch.kekelidze.krakentrader.optimize.service.StrategyOptimizationService;
import ch.kekelidze.krakentrader.strategy.Strategy;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {CsvFileService.class, ResponseConverterUtils.class,
        BackTesterService.class, Indicator.class, Strategy.class, Optimizer.class}
)
public class StrategyOptimizationRunner {

  public static void main(String[] args) {
    var application = SpringApplication.run(StrategyOptimizationRunner.class, args);

    // Parse coins from command-line arguments
    if (args.length == 0 || args[0].isBlank()) {
      log.error("No coins were provided as command-line arguments.");
      return;
    }
    List<String> coins = Arrays.asList(args[0].split(","));
    log.debug("Parsed coins: {}", coins);

    if (coins.isEmpty()) {
      log.error("No coins were provided as command-line arguments.");
      return;
    }

    // Create optimization service
    StrategyOptimizationService optimizationService = application.getBean(
        StrategyOptimizationService.class);

    // Run optimization
    log.info("Starting Strategy Optimization");
    optimizationService.optimizeCoinPairs(coins);

    // Print results
    log.info("Best Strategies: {}", optimizationService.getBestStrategiesMap());
    log.info("Optimized Parameters: {}", optimizationService.getOptimizedParametersMap());
  }
}