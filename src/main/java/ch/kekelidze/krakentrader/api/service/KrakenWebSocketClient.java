package ch.kekelidze.krakentrader.api.service;

import ch.kekelidze.krakentrader.trade.service.TradeStrategyService;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

@Service
@ClientEndpoint
@RequiredArgsConstructor
public class KrakenWebSocketClient {

  private static List<Double> closes = new ArrayList<>();

  private final TradeStrategyService tradeStrategyService;

  @OnOpen
  public void onOpen(Session session) {
    System.out.println("Connected to Kraken WebSocket");
    String subscribeMsg = "{\"event\":\"subscribe\", \"pair\":[\"XRP/USD\"], \"subscription\":{\"name\":\"ohlc\"}}";
    session.getAsyncRemote().sendText(subscribeMsg);
  }

  @OnMessage
  public void onMessage(String message) {
    JSONObject json = new JSONObject(message);
    if (json.has("event")) {
      return; // Ignore heartbeat/status messages
    }

    // Parse OHLC data (format: ["channelID", ["time", "open", "high", "low", "close", ...], ...])
    JSONArray data = json.getJSONArray(json.keys().next());
    double closePrice = data.getJSONArray(1).getDouble(4); // Close price is at index 4
    closes.add(closePrice);

    if (closes.size() >= 21) { // Wait for enough data
      tradeStrategyService.executeStrategy("XRP", closes, closePrice);
    }
  }
}
