package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.file.service.CsvFileService;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.optimize.Optimizer;
import ch.kekelidze.krakentrader.optimize.service.BuyLowSellHighOptimizationService;
import ch.kekelidze.krakentrader.strategy.Strategy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {CsvFileService.class, ResponseConverterUtils.class, Optimizer.class,
        TradingApiService.class, BackTesterService.class, Indicator.class, Strategy.class}
)
public class BuyLowSellHighParameterOptimizationRunner {

  public static void main(String[] args) {
    var application = SpringApplication.run(BuyLowSellHighParameterOptimizationRunner.class, args);
    var optimizationService = application.getBean(BuyLowSellHighOptimizationService.class);

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

    int period = Integer.parseInt(args[1]);
    ZonedDateTime startDate = LocalDate.parse(args[2], DateTimeFormatter.ISO_DATE)
        .atStartOfDay(ZoneId.systemDefault());
    ZonedDateTime endDate = args.length > 3
        ? LocalDate.parse(args[3], DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneId.systemDefault())
        : LocalDate.now().atStartOfDay(ZoneId.systemDefault());


    // Run optimization
    log.info("Starting Strategy Optimization for {}", coins);
    optimizationService.optimizeCoinPairs(coins, period, startDate, endDate);
  }
}