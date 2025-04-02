package ch.kekelidze.krakentrader.api;

import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ta4j.core.Bar;

@Slf4j
@ClientEndpoint
public class KrakenWebSocketClient {

  private static final List<Bar> closes = new ArrayList<>();

  private static TradeService tradeService;
  private static ResponseConverterUtils responseConverterUtils;

  // Method to set dependencies from Spring context
  public static void initialize(TradeService strategyService,
      ResponseConverterUtils converterUtils) {
    tradeService = strategyService;
    responseConverterUtils = converterUtils;
  }

  @OnOpen
  public void onOpen(Session session) {
    log.info("Connected to Kraken WebSocket");
    String subscribeMsg = """
        {
            "method": "subscribe",
            "params": {
                "channel": "ohlc",
                "symbol": [
                    "XRP/USD"
                ],
                "interval": 1
            }
        }
        """;
    session.getAsyncRemote().sendText(subscribeMsg);
  }

  @OnMessage
  public void onMessage(String message) {
    // Check if dependencies are set
    if (responseConverterUtils == null || tradeService == null) {
      throw new RuntimeException("Dependencies not set. Please call initialize() first.");
    }

    JSONObject json = new JSONObject(message);
    if (json.has("event")) {
      return; // Ignore heartbeat/status messages
    }

    log.info("Received message: {}", message);
    // Parse OHLC data (format: ["channelID", ["time", "open", "high", "low", "close", ...], ...])
    JSONArray data = json.getJSONArray(json.keys().next());
    var bar = responseConverterUtils.getPriceBar(data.getJSONArray(1), 60);
    closes.add(bar);

    // Wait for enough data
    if (closes.size() >= 21) {
      //TODO pass model and parameters
      tradeService.executeStrategy(closes, null);
    }
  }
}
