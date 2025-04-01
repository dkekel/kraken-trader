package ch.kekelidze.krakentrader.api;

import ch.kekelidze.krakentrader.api.service.KrakenWebSocketService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WebSocketRunner {

  @Bean
  public CommandLineRunner startWebSocketClient(KrakenWebSocketService service) {
    return args -> {
      service.startWebSocketClient();
      Thread.currentThread().join();
    };
  }
}
