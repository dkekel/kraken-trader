package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.rest.service.KrakenApiService;
import ch.kekelidze.krakentrader.optimize.service.CsvFileService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.Indicator;
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
    scanBasePackageClasses = {KrakenApiService.class, ResponseConverterUtils.class,
        BackTesterService.class, Indicator.class, Strategy.class, Optimizer.class}
)
public class KrakenParameterOptimizerApplication {

  private static final double INITIAL_CAPITAL = 10000;

  public static void main(String[] args) throws IOException {
    String coin = args[0];
    int trainingInterval = Integer.parseInt(args[1]);
    var application = SpringApplication.run(KrakenParameterOptimizerApplication.class, args);
    optimizeAndValidate(application, coin, trainingInterval);
  }

  private static void optimizeAndValidate(ApplicationContext application, String coin, int period)
      throws IOException {
    var fileName = coin + "_" + period;
    var krakenCsvService = application.getBean(CsvFileService.class);
    var optimizer = application.getBean("walkForwardOptimizer", Optimizer.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = krakenCsvService.readCsvFile("data/" + fileName + ".csv");
    var evaluationContext = EvaluationContext.builder().symbol(getValidCoinName(coin))
        .bars(historicalData).build();
    //TODO replace API service with CSV data load for parameter optimisation
    var optimizeParameters = optimizer.optimizeParameters(evaluationContext);
    log.info("Optimised strategy: {}", optimizeParameters);

    // 30% validation
    int trainingSize = (int) (historicalData.size() * 0.7);
    var validationData = historicalData.subList(trainingSize, historicalData.size());

    var evaluationContextWithValidation = EvaluationContext.builder().symbol(coin)
        .bars(validationData).build();
    var result = backtestService.runSimulation(evaluationContextWithValidation, optimizeParameters,
        INITIAL_CAPITAL);
    log.info("Trade result: {}", result);
  }

  private static String getValidCoinName(String coinPair) {
    if (coinPair != null && coinPair.endsWith("USD")) {
      return coinPair.substring(0, coinPair.length() - 3) + "/" + coinPair.substring(
          coinPair.length() - 3);
    }
    return coinPair;
  }
}
