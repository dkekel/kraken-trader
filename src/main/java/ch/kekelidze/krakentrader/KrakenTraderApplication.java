package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.service.KrakenCsvService;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.optimize.Optimizer;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication
public class KrakenTraderApplication {

  public static void main(String[] args) throws IOException {
    var application = SpringApplication.run(KrakenTraderApplication.class, args);
//    optimizeMLModel(application);
//    optimizeAndValidate(application);
    recentDataTrade(application);
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
    var tradeService = application.getBean(TradeService.class);
    var historicalData = krakenCsvService.readCsvFile("data/XRPUSD_60_Q4_2024.csv");
    var optimizeParameters = optimizer.optimizeParameters(historicalData);
    log.info("Optimised strategy: {}", optimizeParameters);

    // 30% validation
    int trainingSize = (int) (historicalData.size() * 0.7);
    var validationData = historicalData.subList(trainingSize, historicalData.size());

    tradeService.executeStrategy(validationData, optimizeParameters);
  }

  private static void recentDataTrade(ApplicationContext application)
      throws IOException {
    var coin = "XRPUSD";
    var krakenApiService = application.getBean(KrakenApiService.class);
    var tradeService = application.getBean(TradeService.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = krakenApiService.queryHistoricalData(coin, 5);
//    tradeService.executeStrategy(historicalData, getStrategyParameters());
    var result = backtestService.runSimulation(historicalData, getStrategyParameters(), 10000);
    log.info("Trade result: {}", result);
  }

  private static StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(9).movingAverageLongPeriod(21)
        .rsiBuyThreshold(30).rsiSellThreshold(70).rsiPeriod(14)
        .macdShortBarCount(12).macdLongBarCount(26).macdBarCount(9)
        .adxPeriod(14).adxBullishThreshold(25).adxBearishThreshold(30)
        .lossPercent(5).profitPercent(10)
        .volumePeriod(20)
        .weightedAgreementThreshold(55)
        .build();
  }
}
