package ch.kekelidze.krakentrader.api.websocket.service;

import ch.kekelidze.krakentrader.api.websocket.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.api.rest.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenWebSocketService {

  private static final String WS_URL = "wss://ws.kraken.com/v2";

  private final Portfolio portfolio;
  private final TradeService tradeService;
  private final ResponseConverterUtils responseConverterUtils;
  private final KrakenApiService krakenApiService;
  private final ApplicationContext applicationContext;
  
  public void startWebSocketClient(String[] args) {
    try {
      var strategy = applicationContext.getBean(args[0], Strategy.class);
      tradeService.setStrategy(strategy);
      portfolio.setTotalCapital(2000);

      // Initialize the WebSocket client with Spring-managed dependencies
      KrakenWebSocketClient.initialize(tradeService, responseConverterUtils, krakenApiService);

      // Connect to WebSocket server
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      try (var session = container.connectToServer(KrakenWebSocketClient.class,
          URI.create(WS_URL))) {
        log.info("WebSocket client started and connected");
        Thread.currentThread().join();
      }
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}