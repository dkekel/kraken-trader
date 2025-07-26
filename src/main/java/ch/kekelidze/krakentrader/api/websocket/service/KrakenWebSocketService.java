package ch.kekelidze.krakentrader.api.websocket.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.api.websocket.SinglePairWebSocketClient;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.TradeState;
import ch.kekelidze.krakentrader.trade.entity.TradeStateEntity;
import ch.kekelidze.krakentrader.trade.repository.TradeStateRepository;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import ch.kekelidze.krakentrader.trade.service.TradeStatePersistenceService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenWebSocketService implements DisposableBean {

  private static final String WS_URL = "wss://ws.kraken.com/v2";
  private static final int RECONNECT_DELAY_MS = 2000;
  private static final int SEND_TIMEOUT_MS = 10000;
  private static final int SESSION_IDLE_TIMEOUT_MS = 30000;
  private final List<Session> activeSessions = new ArrayList<>();
  private final Map<KrakenWebSocketClient, String> clientToCoinPairMap = new HashMap<>();

  private final Portfolio portfolio;
  private final TradeService tradeService;
  private final ResponseConverterUtils responseConverterUtils;
  private final TradingApiService krakenApiService;
  private final HistoricalDataService marketDataService;
  private final ApplicationContext applicationContext;
  private final TradeStatePersistenceService tradeStatePersistenceService;
  private final TradeStateRepository tradeStateRepository;

  private WebSocketContainer container;

  public void startWebSocketClient(String[] args) {
    try {
      var strategy = applicationContext.getBean(args[0], Strategy.class);
      var coinPairs = args[1].split(",");

      // Update actively traded status for all coins
      updateActivelyTradedStatus(coinPairs);
      
      initFeesCache(coinPairs);

      try {
        double capital = krakenApiService.getAssetBalance("USD");
        portfolio.setTotalCapital(capital);
        log.info("Retrieved capital from Kraken API: {}", capital);
      } catch (Exception e) {
        log.error("Failed to get account balance from Kraken API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to get account balance from Kraken API", e);
      }

      log.info("Starting WebSocket client for strategy: {}", strategy);
      tradeService.setStrategy(strategy);

      // Portfolio allocation is now calculated dynamically based on coins not in trade
      log.info("Trading {} coin pairs", coinPairs.length);

      // Initialize the WebSocket client with Spring-managed dependencies
      KrakenWebSocketClient.initialize(tradeService, responseConverterUtils, marketDataService,
          coinPairs, this);

      // Connect to WebSocket server
      var container = getWebSocketContainer();
      for (String coinPair : coinPairs) {
        createClientForCoinPair(coinPair, container);
        log.info("WebSocket client started and connected for coin pair: {}", coinPair);
      }

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void initFeesCache(String[] coinPairs) {
    for (String coinPair : coinPairs) {
      var coinFees = krakenApiService.getCoinTradingFee(coinPair);
      log.info("Initialised trading fee cache for {}: {}", coinPair, coinFees);
    }
  }
  
  /**
   * Updates the activelyTraded status for all coins in the database.
   * Sets activelyTraded=true for coins in the provided list and activelyTraded=false for all others.
   *
   * @param coinPairs Array of coin pairs that are actively traded
   */
  private void updateActivelyTradedStatus(String[] coinPairs) {
    log.info("Updating actively traded status for all coins");
    
    // Create a set of coin pairs for faster lookup
    Set<String> activeCoinPairs = new HashSet<>(Arrays.asList(coinPairs));
    
    // Get all trade states from the database
    List<TradeStateEntity> allTradeStates = tradeStateRepository.findAll();
    
    // Update each trade state
    for (TradeStateEntity entity : allTradeStates) {
      boolean isActivelyTraded = activeCoinPairs.contains(entity.getCoinPair());
      entity.setActivelyTraded(isActivelyTraded);
      
      // Log the status change
      if (isActivelyTraded) {
        log.info("Setting {} as actively traded", entity.getCoinPair());
      } else {
        log.info("Setting {} as not actively traded", entity.getCoinPair());
      }
    }
    
    // Save all updated entities
    tradeStateRepository.saveAll(allTradeStates);
    
    // Create trade states for new coin pairs that don't exist in the database yet
    for (String coinPair : coinPairs) {
      if (allTradeStates.stream().noneMatch(e -> e.getCoinPair().equals(coinPair))) {
        TradeState newTradeState = new TradeState(coinPair);
        newTradeState.setActivelyTraded(true);
        tradeStatePersistenceService.saveTradeState(newTradeState);
        log.info("Created new trade state for {} and set as actively traded", coinPair);
      }
    }
    
    log.info("Finished updating actively traded status for all coins");
  }

  @Override
  public void destroy() {
    shutdown();
  }

  private void shutdown() {
    log.info("Shutting down all WebSocket connections...");
    for (Session session : activeSessions) {
      try {
        if (session.isOpen()) {
          session.close();
        }
      } catch (Exception e) {
        log.error("Error closing WebSocket session", e);
      }
    }
    activeSessions.clear();
    clientToCoinPairMap.clear();
    log.info("All WebSocket connections closed");
  }

  /**
   * Reconnects a client that has detected a connection issue.
   * 
   * @param client The client that needs to be reconnected
   */
  public void reconnectClient(KrakenWebSocketClient client) {
    String coinPair = clientToCoinPairMap.get(client);
    if (coinPair == null) {
      log.error("Cannot reconnect client: no coin pair found for client {}", client);
      return;
    }

    log.info("Reconnecting client for coin pair: {}", coinPair);

    Session existingSession = null;
    for (Session session : new ArrayList<>(activeSessions)) {
      if (session.getUserProperties().get("client") == client) {
        existingSession = session;
        break;
      }
    }

    if (existingSession != null) {
      try {
        activeSessions.remove(existingSession);
        if (existingSession.isOpen()) {
          existingSession.close();
        }
      } catch (Exception e) {
        log.error("Error closing existing session for coin pair {}: {}", coinPair, e.getMessage(), e);
      }
    }

    clientToCoinPairMap.remove(client);
    client.destroy();

    CompletableFuture.runAsync(reconnectRunnable(coinPair, RECONNECT_DELAY_MS, 1));
  }

  /**
   * Schedules a reconnection attempt with exponential backoff.
   * 
   * @param coinPair The coin pair to reconnect
   * @param attempt The current attempt number (starting from 1)
   */
  private void scheduleReconnectWithBackoff(String coinPair, int attempt) {
    // Maximum number of reconnection attempts
    final int MAX_RECONNECT_ATTEMPTS = 10;

    if (attempt > MAX_RECONNECT_ATTEMPTS) {
      log.error("Maximum reconnection attempts ({}) reached for coin pair {}. Giving up.", 
          MAX_RECONNECT_ATTEMPTS, coinPair);
      return;
    }

    // Calculate delay with exponential backoff: base_delay * 2^(attempt-1)
    // This gives: 2s, 4s, 8s, 16s, 32s, etc.
    // Cap the maximum delay at 5 minutes
    final long MAX_DELAY_MS = 5 * 60 * 1000;

    // Calculate the final delay with jitter and capping
    final long delayMs = Math.min(
        RECONNECT_DELAY_MS * (long)Math.pow(2, attempt - 1) 
            + (long)(RECONNECT_DELAY_MS * Math.pow(2, attempt - 1) * 0.1 * Math.random()),
        MAX_DELAY_MS
    );

    log.info("Scheduling reconnection attempt {} for coin pair {} after {} ms", 
        attempt, coinPair, delayMs);

    CompletableFuture.runAsync(reconnectRunnable(coinPair, delayMs, attempt));
  }

  private Runnable reconnectRunnable(String coinPair, long delayMs, int attempt) {
    return () -> {
      try {
        Thread.sleep(delayMs);

        log.info("Attempting reconnection #{} for coin pair: {}", attempt, coinPair);
        var container = getWebSocketContainer();
        createClientForCoinPair(coinPair, container);
        log.info("Client successfully reconnected for coin pair: {} after {} attempts",
            coinPair, attempt);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.error("Reconnection interrupted for coin pair {}: {}", coinPair, e.getMessage());
      } catch (Exception e) {
        log.error("Error during reconnection attempt {} for coin pair {}: {}",
            attempt, coinPair, e.getMessage(), e);
        // Schedule next attempt with increased backoff
        scheduleReconnectWithBackoff(coinPair, attempt + 1);
      }
    };
  }

  private synchronized WebSocketContainer getWebSocketContainer() {
    if (container == null) {
      container = ContainerProvider.getWebSocketContainer();
      container.setAsyncSendTimeout(SEND_TIMEOUT_MS);
      container.setDefaultMaxSessionIdleTimeout(SESSION_IDLE_TIMEOUT_MS);
    }
    return container;
  }

  private void createClientForCoinPair(String coinPair,
      WebSocketContainer container) throws DeploymentException, IOException {
    var newClient = new SinglePairWebSocketClient(coinPair);
    var session = container.connectToServer(
        newClient,
        URI.create(WS_URL)
    );

    session.getUserProperties().put("client", newClient);

    activeSessions.add(session);
    clientToCoinPairMap.put(newClient, coinPair);
  }
}
