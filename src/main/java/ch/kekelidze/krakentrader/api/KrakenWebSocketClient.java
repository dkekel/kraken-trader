package ch.kekelidze.krakentrader.api;

import static ch.kekelidze.krakentrader.api.service.KrakenWebSocketService.getStrategyParameters;

import ch.kekelidze.krakentrader.api.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedList;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ta4j.core.Bar;

@Slf4j
@ClientEndpoint
public class KrakenWebSocketClient {

  private static final String CHANNEL = "channel";
  private static final String STATUS = "status";
  private static final String OHLC = "ohlc";
  private static final String SYMBOL = "XRP/USD";

  private static final Deque<Bar> priceQueue = new LinkedList<>();

  private static TradeService tradeService;
  private static ResponseConverterUtils responseConverterUtils;

  // Method to set dependencies from Spring context
  public static void initialize(TradeService strategyService, ResponseConverterUtils converterUtils,
      KrakenApiService krakenApiService) {
    tradeService = strategyService;
    responseConverterUtils = converterUtils;
    initializePriceQueue(krakenApiService);
  }

  private static void initializePriceQueue(KrakenApiService krakenApiService) {
    var historicalData = krakenApiService.queryHistoricalData(SYMBOL, 5);
    for (Bar bar : historicalData) {
      if (priceQueue.size() >= 30) {
        priceQueue.pollFirst();
      }
      priceQueue.addLast(bar);
    }
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
                    "%s"
                ],
                "interval": 5
            }
        }
        """.formatted(SYMBOL);
    session.getAsyncRemote().sendText(subscribeMsg);
  }

  @OnMessage
  public void onMessage(String message) {
    // Check if dependencies are set
    if (responseConverterUtils == null || tradeService == null) {
      throw new RuntimeException("Dependencies not set. Please call initialize() first.");
    }

    JSONObject json = new JSONObject(message);
    var channel = getChannel(json);
    if (STATUS.equals(channel)) {
      log.debug("Ignore heartbeat/status messages");
      return;
    }

    if (OHLC.equals(channel)) {
      log.info("Received message: {}", message);

      JSONArray data = json.getJSONArray("data");
      for (int i = 0; i < data.length(); i++) {
        JSONObject ohlcObject = data.getJSONObject(i);
        var ohlcEntry = responseConverterUtils.convertJsonToOhlcEntry(ohlcObject);
        var bar = responseConverterUtils.getPriceBarFromOhlcEntry(ohlcEntry);
        if (priceQueue.size() >= 30) {
          priceQueue.pollFirst();
        }
        priceQueue.addLast(bar);
      }

      // Wait for enough data
      if (priceQueue.size() >= 21) {
        log.info("Enough data received. Executing strategy...");
        tradeService.executeStrategy(new ArrayList<>(priceQueue), getStrategyParameters());
      }
    }
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.error("WebSocket error: {}", throwable.getMessage(), throwable);
  }

  private String getChannel(JSONObject json) {
    return json.has(CHANNEL) ? String.valueOf(json.get(CHANNEL)) : "";
  }
}
