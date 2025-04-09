package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.service.KrakenCsvService;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.optimize.Optimizer;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import java.io.File;
import java.io.IOException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication
public class KrakenTraderApplication {

  private static final double INITIAL_CAPITAL = 10000;

  public static void main(String[] args) throws IOException {
    var application = SpringApplication.run(KrakenTraderApplication.class, args);
//    optimizeMLModel(application);
//    optimizeAndValidate(application);
//    validateWithRecentData(application);
  }

  private static void optimizeMLModel(ApplicationContext application) throws IOException {
    var krakenCsvService = application.getBean(KrakenCsvService.class);
    var optimizer = application.getBean("walkForwardOptimizer", Optimizer.class);
    var historicalData = krakenCsvService.readCsvFile("data/XRPUSD_60.csv");
    var modelFile = new File("model_v4.h5");
    if (!modelFile.exists()) {
      var model = optimizer.trainModel(historicalData);
      model.save(modelFile);
    }
  }

  private static void optimizeAndValidate(ApplicationContext application) throws IOException {
    var krakenCsvService = application.getBean(KrakenCsvService.class);
    var optimizer = application.getBean("walkForwardOptimizer", Optimizer.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = krakenCsvService.readCsvFile("data/XRPUSD_60_Q4_2024.csv");
    var evaluationContext = EvaluationContext.builder().symbol("XRP/USD").bars(historicalData)
        .build();
    var optimizeParameters = optimizer.optimizeParameters(evaluationContext);
    log.info("Optimised strategy: {}", optimizeParameters);

    // 30% validation
    int trainingSize = (int) (historicalData.size() * 0.7);
    var validationData = historicalData.subList(trainingSize, historicalData.size());

    var evaluationContextWithValidation = EvaluationContext.builder().symbol("XRP/USD")
        .bars(validationData).build();
    var result = backtestService.runSimulation(evaluationContextWithValidation, optimizeParameters,
        INITIAL_CAPITAL);
    log.info("Trade result: {}", result);
  }

  private static void validateWithRecentData(ApplicationContext application)
      throws IOException {
    var coin = "XRP/USD";
    var krakenApiService = application.getBean(KrakenApiService.class);
    var tradeService = application.getBean(TradeService.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = krakenApiService.queryHistoricalData(List.of(coin), 5);
    var evaluationContext = EvaluationContext.builder().symbol(coin).bars(historicalData.get(coin))
        .build();
    var result = backtestService.runSimulation(evaluationContext, INITIAL_CAPITAL);
    log.info("Trade result: {}", result);
  }
}
