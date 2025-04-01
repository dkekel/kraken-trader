package ch.kekelidze.krakentrader.api;

import ch.kekelidze.krakentrader.api.service.KrakenWebSocketClient;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;

public class WebSocketRunner {

  private static final String WS_URL = "wss://ws.kraken.com";

  public static void main(String[] args) throws Exception {
    WebSocketContainer container = ContainerProvider.getWebSocketContainer();
    container.connectToServer(KrakenWebSocketClient.class, URI.create(WS_URL));
    Thread.sleep(Long.MAX_VALUE); // Keep running
  }
}
