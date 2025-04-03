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
    var startTime = (System.currentTimeMillis() / 1000L - (180L * 24 * 60 * 60));
    var historicalData = krakenApiService.queryHistoricalData(coin, 5, startTime);

    var modelFile = new File("model_v3.h5");
    if (!modelFile.exists()) {
      var model = optimizer.trainModel(historicalData);
      model.save(modelFile);
    }

    var optimizeParameters = optimizer.optimizeParameters(historicalData);
    log.info("Optimised strategy: {}", optimizeParameters);

    int trainingSize = (int) (historicalData.size() * 0.7); // 30% validation
    var validationData = historicalData.subList(trainingSize, historicalData.size());

    tradeService.executeStrategy(validationData, optimizeParameters);
  }
}
