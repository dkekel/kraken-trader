package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.rest.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.KrakenWebSocketRunner;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {KrakenWebSocketRunner.class, KrakenApiService.class,
        ResponseConverterUtils.class, Indicator.class, Strategy.class, Portfolio.class}
)
public class KrakenTraderApplication {

  public static void main(String[] args) {
    SpringApplication.run(KrakenTraderApplication.class, args);
  }
}
