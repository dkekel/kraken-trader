package ch.kekelidze.krakentrader.api.websocket;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.service.KrakenWebSocketService;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;
import org.ta4j.core.Bar;

@Slf4j
@ClientEndpoint
public class KrakenWebSocketClient {

  private static final String CHANNEL = "channel";
  private static final String STATUS = "status";
  private static final String HEARTBEAT = "heartbeat";
  private static final String OHLC = "ohlc";
  private static final String PING_MESSAGE = "{\"method\":\"ping\"}";
  private static final long PING_INTERVAL_MS = 5000;
  private static final long PING_TIMEOUT_MS = 2000;

  private static final int MAX_QUEUE_SIZE = 600;
  private static List<String> SYMBOLS;
  //Default period is 1h, overridable from the strategy implementation
  static int PERIOD = 60;

  private static final Map<String, Deque<Bar>> priceQueue = new HashMap<>();

  private static TradeService tradeService;
  private static ResponseConverterUtils responseConverterUtils;
  private static KrakenWebSocketService webSocketService;

  protected Session session;
  protected final AtomicLong lastMessageTimestamp = new AtomicLong(System.currentTimeMillis());
  private ScheduledExecutorService heartbeatExecutor;
  private boolean pingInProgress = false;

  // Method to set dependencies from Spring context
  public static void initialize(TradeService strategyService, ResponseConverterUtils converterUtils,
      HistoricalDataService marketDataService, String[] symbols, KrakenWebSocketService service) {
    tradeService = strategyService;
    responseConverterUtils = converterUtils;
    webSocketService = service;
    SYMBOLS = List.of(symbols);
    PERIOD = tradeService.getStrategy().getPeriod();
    initializePriceQueue(marketDataService);
  }

  private static void initializePriceQueue(HistoricalDataService marketDataService) {
    var historicalData = marketDataService.queryHistoricalData(SYMBOLS, PERIOD);
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
    this.session = session;
    lastMessageTimestamp.set(System.currentTimeMillis());

    var symbols = String.join(",", SYMBOLS.stream().map(s -> "\"" + s + "\"").toList());
    var subscribeMsg = getSubscribeMessage(symbols);
    session.getAsyncRemote().sendText(subscribeMsg);

    startHeartbeat();
  }

  protected void startHeartbeat() {
    if (heartbeatExecutor != null) {
      heartbeatExecutor.shutdownNow();
    }

    heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
    heartbeatExecutor.scheduleAtFixedRate(this::checkConnection, 
        PING_INTERVAL_MS, PING_INTERVAL_MS, TimeUnit.MILLISECONDS);
    log.info("Heartbeat mechanism started");
  }

  private void checkConnection() {
    if (session == null || !session.isOpen()) {
      log.warn("Session is null or closed, requesting reconnection");
      stopHeartbeat();
      requestReconnection();
      return;
    }

    long timeSinceLastMessage = System.currentTimeMillis() - lastMessageTimestamp.get();
    if (timeSinceLastMessage >= PING_INTERVAL_MS) {
      if (pingInProgress) {
        log.warn("Previous ping did not receive a response, requesting reconnection");
        stopHeartbeat();
        requestReconnection();
        return;
      }

      try {
        log.debug("Sending ping to keep connection alive");
        pingInProgress = true;
        session.getAsyncRemote().sendText(PING_MESSAGE);

        // Schedule a task to check if ping was responded to
        heartbeatExecutor.schedule(() -> {
          if (pingInProgress) {
            log.warn("Ping timeout, requesting reconnection");
            stopHeartbeat();
            requestReconnection();
          }
        }, PING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
      } catch (Exception e) {
        log.error("Error sending ping: {}", e.getMessage(), e);
        stopHeartbeat();
        requestReconnection();
      }
    }
  }

  private void stopHeartbeat() {
    if (heartbeatExecutor != null) {
      heartbeatExecutor.shutdownNow();
      heartbeatExecutor = null;
    }
  }

  private void requestReconnection() {
    if (webSocketService != null) {
      log.info("Requesting reconnection");
      webSocketService.reconnectClient(this);
    } else {
      log.error("Cannot reconnect: webSocketService is null");
    }
  }

  protected String getSubscribeMessage(String symbols) {
    return  """
        {
            "method": "subscribe",
            "params": {
                "channel": "ohlc",
                "symbol": [%s],
                "interval": %d
            }
        }
        """.formatted(String.join(",", symbols), PERIOD);
  }

  @OnMessage
  public void onMessage(String message) {
    if (responseConverterUtils == null || tradeService == null) {
      throw new RuntimeException("Dependencies not set. Please call initialize() first.");
    }

    lastMessageTimestamp.set(System.currentTimeMillis());

    if (message.contains("\"method\":\"pong\"")) {
      log.debug("Received pong response");
      pingInProgress = false;
      return;
    }

    JSONObject json = new JSONObject(message);
    var channel = getChannel(json);
    if (STATUS.equals(channel) || HEARTBEAT.equals(channel)) {
      log.trace("Ignore heartbeat/status messages");
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
        tradeService.executeStrategy(symbol, new ArrayList<>(candleQueue));
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
    stopHeartbeat();
    requestReconnection();
  }

  /**
   * Cleans up resources when the client is destroyed.
   * This should be called when the client is no longer needed.
   */
  public void destroy() {
    stopHeartbeat();
    if (session != null && session.isOpen()) {
      try {
        session.close();
      } catch (IOException e) {
        log.error("Error closing session: {}", e.getMessage(), e);
      }
    }
  }
}
