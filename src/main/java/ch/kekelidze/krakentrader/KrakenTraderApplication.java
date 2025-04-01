package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class KrakenTraderApplication {

  public static void main(String[] args) {
    var application = SpringApplication.run(KrakenTraderApplication.class, args);
    var krakenApi = application.getBean(KrakenApiService.class);
    var backtester = application.getBean(BackTesterService.class);
    test(krakenApi, backtester);
  }

  private static void test(KrakenApiService krakenApiService, BackTesterService backTesterService) {
    var coin = "XRPUSD";
    var historicalData = krakenApiService.queryHistoricalData(coin, 1);
    backTesterService.backtest(coin, historicalData);
  }
}
