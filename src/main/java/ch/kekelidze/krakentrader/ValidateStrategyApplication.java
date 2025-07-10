package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.file.service.CsvFileService;
import ch.kekelidze.krakentrader.api.rest.service.MarketDataService;
import ch.kekelidze.krakentrader.api.rest.service.PaperTradeKrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.backtester.service.dto.BacktestResult;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.optimize.config.StrategyConfig;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.util.TradingCircuitBreaker;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {CsvFileService.class, MarketDataService.class,
        PaperTradeKrakenApiService.class, ResponseConverterUtils.class, BackTesterService.class,
        StrategyConfig.class, Indicator.class, Strategy.class, TradingCircuitBreaker.class}
)
public class ValidateStrategyApplication {

  private static final double INITIAL_CAPITAL = 100;

  public static void main(String[] args) {
    List<String> coins = List.of(args[0].split(","));
    int period = Integer.parseInt(args[1]);

    ZonedDateTime startDate = args.length > 2
        ? LocalDate.parse(args[2], DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneId.systemDefault())
        : LocalDate.of(2000, 1, 1).atStartOfDay(ZoneId.systemDefault());
    ZonedDateTime endDate = args.length > 3
        ? LocalDate.parse(args[3], DateTimeFormatter.ISO_DATE).atStartOfDay(ZoneId.systemDefault())
        : LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault());

    var application = SpringApplication.run(ValidateStrategyApplication.class, args);
    validateWithHistoricalData(application, coins, period, startDate, endDate);
    int exitCode = SpringApplication.exit(application, () -> 0);
    System.exit(exitCode);
  }

  private static void validateWithHistoricalData(ApplicationContext application, List<String> coins,
      int period, ZonedDateTime startDate, ZonedDateTime endDate) {
    var historicalDataService = application.getBean(HistoricalDataService.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = historicalDataService.queryHistoricalData(coins, period);
    var results = new java.util.HashMap<String, BacktestResult>();

    for (String coin : coins) {
      log.info("Validating strategy for {} from {} to {}", coin, startDate, endDate);
      var evaluationContext = EvaluationContext.builder()
          .symbol(coin)
          .period(period)
          .bars(historicalData.get(coin).stream()
              .filter(bar -> checkDateAfter(bar.getEndTime(), startDate))
              .filter(bar -> checkDateBefore(bar.getEndTime(), endDate))
              .toList()
          )
          .build();
      var result = backtestService.runSimulation(evaluationContext, INITIAL_CAPITAL);
      results.put(coin, result);
      log.info("Trade result for {}: {}", coin, result);
    }

    var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    var filename = String.format("results/strategy_results_%s.md", timestamp);
    writeResultsToMarkdownFile(filename, results);
  }
  
  private static boolean checkDateAfter(ZonedDateTime date, ZonedDateTime afterDate) {
    return date.isAfter(afterDate) || date.isEqual(afterDate);
  }
  
  private static boolean checkDateBefore(ZonedDateTime date, ZonedDateTime beforeDate) {
    return date.isBefore(beforeDate) || date.isEqual(beforeDate);
  }

  private static void writeResultsToMarkdownFile(String filePath,
      Map<String, BacktestResult> results) {
    try (var writer = new FileWriter(filePath)) {
      writer.write("# Strategy Results\n\n");
      for (var entry : results.entrySet()) {
        writer.write(String.format("## %s\n", entry.getKey()));

        BacktestResult result = entry.getValue();
        writer.write(String.format("- Profit: %.2f%%\n", result.totalProfit()));
        writer.write(String.format("- Total Trades: %d\n", result.totalTrades()));
        writer.write(String.format("- Net Profit: %.2f\n", result.capital() - INITIAL_CAPITAL));
        writer.write(String.format("- Win Rate: %.2f%%\n", result.winRate() * 100));
        writer.write(
            String.format("- Largest Drawdown: %.2f%%\n", result.maxDrawdown()));
        writer.write(String.format("- Sharpe Ratio: %.2f\n\n", result.sharpeRatio()));
      }
      writer.flush();
      log.info("Results successfully written to {}", filePath);
    } catch (IOException e) {
      log.error("Failed to write results to file: {}", e.getMessage(), e);
    }
  }
}
