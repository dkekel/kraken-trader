package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.indicator.optimize.Optimizer;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class KrakenTraderApplication {

  public static void main(String[] args) throws IOException {
    var application = SpringApplication.run(KrakenTraderApplication.class, args);
    var krakenApi = application.getBean(KrakenApiService.class);
    var tradeService = application.getBean(TradeService.class);
    var optimizer = application.getBean("walkForwardOptimizer", Optimizer.class);
    optimizeAndTrade(krakenApi, tradeService, optimizer);
  }

  private static void optimizeAndTrade(KrakenApiService krakenApiService,
      TradeService tradeService, Optimizer optimizer) throws IOException {
    var coin = "XRPUSD";
    var historicalData = krakenApiService.queryHistoricalData(coin, 5);
    var optimisedStrategy = optimizer.optimizeParameters(historicalData);
    log.info("Optimised strategy: {}", optimisedStrategy);

    var modelFile = new File("model.h5");
    var model = modelFile.exists() ? MultiLayerNetwork.load(modelFile, true)
        : optimizer.trainModel(historicalData);
    if (!modelFile.exists()) {
      model.save(modelFile);
    }

    int trainingSize = (int) (historicalData.size() * 0.7); // 30% validation
    var validationData = historicalData.subList(trainingSize, historicalData.size());

    tradeService.executeStrategy(validationData, optimisedStrategy);
  }
}
