package ch.kekelidze.krakentrader.api.service;

import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
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
import org.ta4j.core.Bar;

@Service
@ClientEndpoint
@RequiredArgsConstructor
public class KrakenWebSocketClient {

  private static final List<Bar> closes = new ArrayList<>();

  private final TradeStrategyService tradeStrategyService;
  private final ResponseConverterUtils responseConverterUtils;

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
    var bar = responseConverterUtils.getPriceBar(data.getJSONArray(1), 60);
    closes.add(bar);

    if (closes.size() >= 21) { // Wait for enough data
      tradeStrategyService.executeStrategy("XRP", closes, bar.getClosePrice().doubleValue());
    }
  }
}
