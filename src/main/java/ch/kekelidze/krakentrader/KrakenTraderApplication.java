package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.service.strategy.GeneticOptimizer;
import ch.kekelidze.krakentrader.indicator.service.strategy.StrategyParameters;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class KrakenTraderApplication {

  public static void main(String[] args) {
    var application = SpringApplication.run(KrakenTraderApplication.class, args);
    var krakenApi = application.getBean(KrakenApiService.class);
    var backtester = application.getBean(BackTesterService.class);
    var geneticOptimizer = application.getBean(GeneticOptimizer.class);
//    test(krakenApi, backtester);
    optimizeAndTrade(krakenApi, backtester, geneticOptimizer);
  }

  private static void test(KrakenApiService krakenApiService, BackTesterService backTesterService) {
    var coin = "XRPUSD";
    var historicalData = krakenApiService.queryHistoricalData(coin, 60);
    for (var data : historicalData) {
      log.info("{}", data);
    }
    var strategyParams = new StrategyParameters(14, 26, 10, 33.0, 70.0);
    backTesterService.backtest(historicalData, strategyParams);
  }

  private static void optimizeAndTrade(KrakenApiService krakenApiService,
      BackTesterService backTesterService, GeneticOptimizer geneticOptimizer) {
    var coin = "XRPUSD";
    var historicalData = krakenApiService.queryHistoricalData(coin, 60);
    var optimisedStrategy = geneticOptimizer.optimize(historicalData);
    log.info("Optimised strategy: {}", optimisedStrategy);
    backTesterService.backtest(historicalData, optimisedStrategy);
  }
}
