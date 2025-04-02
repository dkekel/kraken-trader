package ch.kekelidze.krakentrader.api.service;

import ch.kekelidze.krakentrader.api.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenWebSocketService {

  private static final String WS_URL = "wss://ws.kraken.com/v2";

  private final TradeService tradeService;
  private final ResponseConverterUtils responseConverterUtils;
  
  public void startWebSocketClient() {
    try {
      // Initialize the WebSocket client with Spring-managed dependencies
      KrakenWebSocketClient.initialize(tradeService, responseConverterUtils);
      
      // Connect to WebSocket server
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      try (var session = container.connectToServer(KrakenWebSocketClient.class,
          URI.create(WS_URL))) {
        log.info("WebSocket client started and connected");
      }
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}