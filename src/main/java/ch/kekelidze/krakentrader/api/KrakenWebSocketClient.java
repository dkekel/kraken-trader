package ch.kekelidze.krakentrader.api;

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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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
  private static final List<String> SYMBOLS = List.of("DOGE/USD", "XRP/USD", "ETH/USD", "HONEY/USD",
      "PEPE/USD", "FLR/USD", "SGB/USD");

  private static final int MAX_QUEUE_SIZE = 600;
  //Default period is 1h, overridable from the strategy implementation
  private static int PERIOD = 60;

  private static final Map<String, Deque<Bar>> priceQueue = new HashMap<>();

  private static TradeService tradeService;
  private static ResponseConverterUtils responseConverterUtils;

  // Method to set dependencies from Spring context
  public static void initialize(TradeService strategyService, ResponseConverterUtils converterUtils,
      KrakenApiService krakenApiService) {
    tradeService = strategyService;
    responseConverterUtils = converterUtils;
    PERIOD = tradeService.getStrategy().getPeriod();
    initializePriceQueue(krakenApiService);
  }

  private static void initializePriceQueue(KrakenApiService krakenApiService) {
    var historicalData = krakenApiService.queryHistoricalData(SYMBOLS, PERIOD);
    for (String coin : SYMBOLS) {
      var historicalCoinData = historicalData.get(coin);
      var coinQueue = priceQueue.computeIfAbsent(coin, key -> new LinkedList<>());
      for (Bar bar : historicalCoinData) {
        enqueueNewBar(bar, coinQueue);
      }
    }
  }

  @OnOpen
  public void onOpen(Session session) {
    log.info("Connected to Kraken WebSocket");
    var symbols = String.join(",", SYMBOLS.stream().map(s -> "\"" + s + "\"").toList());
    String subscribeMsg = """
        {
            "method": "subscribe",
            "params": {
                "channel": "ohlc",
                "symbol": [%s],
                "interval": %d
            }
        }
        """.formatted(String.join(",", symbols), PERIOD);
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

    if (OHLC.equals(channel) && isUpdateMessage(json)) {
      log.debug("Received message: {}", message);

      JSONArray data = json.getJSONArray("data");
      for (int i = 0; i < data.length(); i++) {
        JSONObject ohlcObject = data.getJSONObject(i);
        var ohlcEntry = responseConverterUtils.convertJsonToOhlcEntry(ohlcObject);
        var bar = responseConverterUtils.getPriceBarFromOhlcEntry(ohlcEntry);
        var symbol = ohlcEntry.symbol();
        var candleQueue = priceQueue.get(symbol);
        if (isUpdatedCandle(symbol, bar)) {
          var lastBar = candleQueue.peekLast();
          lastBar.addPrice(bar.getClosePrice());
        } else {
          enqueueNewBar(bar, candleQueue);
        }

        if (candleQueue.size() < MAX_QUEUE_SIZE) {
          log.debug("Candle queue size is too small for {}: {}", symbol, candleQueue.size());
          continue;
        }

        log.debug("Triggering strategy evaluation for {} at {}", symbol, bar.getEndTime());
        new Thread(
            () -> tradeService.executeStrategy(symbol, new ArrayList<>(candleQueue))).start();
      }
    }
  }

  private String getChannel(JSONObject json) {
    return json.has(CHANNEL) ? String.valueOf(json.get(CHANNEL)) : "";
  }

  private boolean isUpdateMessage(JSONObject json) {
    return json.has("type") && "update".equals(json.get("type"));
  }

  private boolean isUpdatedCandle(String symbol, Bar bar) {
    var candleQueue = priceQueue.get(symbol);
    var lastBar = candleQueue.peekLast();
    return lastBar != null && lastBar.getEndTime().isEqual(bar.getEndTime());
  }

  private static void enqueueNewBar(Bar bar, Deque<Bar> priceQueue) {
    if (priceQueue.size() >= MAX_QUEUE_SIZE) {
      priceQueue.pollFirst();
    }
    priceQueue.addLast(bar);
  }

  @OnError
  public void onError(Session session, Throwable throwable) {
    log.error("WebSocket error: {}", throwable.getMessage(), throwable);
  }
}
