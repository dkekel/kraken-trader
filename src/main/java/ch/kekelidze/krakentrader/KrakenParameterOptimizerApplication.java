package ch.kekelidze.krakentrader;

import static ch.kekelidze.krakentrader.strategy.service.StrategyParametersService.getValidCoinName;

import ch.kekelidze.krakentrader.api.file.service.CsvFileService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import ch.kekelidze.krakentrader.optimize.Optimizer;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {CsvFileService.class, ResponseConverterUtils.class,
        BackTesterService.class, Indicator.class, Strategy.class, Optimizer.class}
)
public class KrakenParameterOptimizerApplication {

  private static final double INITIAL_CAPITAL = 100;

  public static void main(String[] args) throws IOException {
    String coin = args[0];
    int trainingInterval = Integer.parseInt(args[1]);
    var application = SpringApplication.run(KrakenParameterOptimizerApplication.class, args);
    optimizeAndValidate(application, coin, trainingInterval);
  }

  private static void optimizeAndValidate(ApplicationContext application, String coin, int period) {
    var fileName = coin + "_" + period;
    var krakenCsvService = application.getBean(CsvFileService.class);
    var optimizer = application.getBean("buyLowSellHighOptimizer", Optimizer.class);
    var backtestService = application.getBean(BackTesterService.class);
    var strategyParametersService = application.getBean(StrategyParametersService.class);
    var historicalData = krakenCsvService.readCsvFile("data/Q4/" + fileName + ".csv");

    var validCoinName = getValidCoinName(coin);
    var evaluationContext = EvaluationContext.builder().symbol(validCoinName)
        .period(period).bars(historicalData).build();
    var optimizeParameters = optimizer.optimizeParameters(evaluationContext);
    log.info("Optimised strategy: {}", optimizeParameters);

    // Save the optimized parameters to the database
    strategyParametersService.saveStrategyParameters(validCoinName, optimizeParameters);
    log.info("Saved optimized parameters for {} to database", validCoinName);

    var evaluationContextWithValidation = EvaluationContext.builder().symbol(coin).period(period)
        .bars(historicalData).build();
    var result = backtestService.runSimulation(evaluationContextWithValidation, optimizeParameters,
        INITIAL_CAPITAL);
    log.info("Trade result: {}", result);
  }
}
