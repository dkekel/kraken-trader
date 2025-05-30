package ch.kekelidze.krakentrader.api.websocket;

import ch.kekelidze.krakentrader.api.websocket.service.KrakenWebSocketService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KrakenWebSocketRunner {

  @Bean
  public CommandLineRunner startWebSocketClient(KrakenWebSocketService service) {
    return service::startWebSocketClient;
  }
}
