package ch.kekelidze.krakentrader.api.websocket;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ClientEndpoint
public class SinglePairWebSocketClient extends KrakenWebSocketClient {

  private final String coinPair;

  public SinglePairWebSocketClient(String coinPair) {
    this.coinPair = coinPair;
  }

  @OnOpen
  @Override
  public void onOpen(Session session) {
    try {
      var subscribeMsg = getSubscribeMessage("\"" + coinPair + "\"");
      session.getAsyncRemote().sendText(subscribeMsg);
      log.info("Subscribed to {} OHLC data", coinPair);
    } catch (Exception e) {
      log.error("Error in onOpen for {}: {}", coinPair, e.getMessage(), e);
    }
  }
}
