package ch.kekelidze.krakentrader.api.websocket.service;

import ch.kekelidze.krakentrader.api.websocket.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.api.rest.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.SinglePairWebSocketClient;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
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
  private final List<Session> activeSessions = new ArrayList<>();

  private final Portfolio portfolio;
  private final TradeService tradeService;
  private final ResponseConverterUtils responseConverterUtils;
  private final KrakenApiService krakenApiService;
  private final ApplicationContext applicationContext;

  public void startWebSocketClient(String[] args) {
    try {
      var strategy = applicationContext.getBean(args[0], Strategy.class);
      var coinPairs = args[1].split(",");
      var capital = Double.parseDouble(args[2]);
      log.info("Starting WebSocket client for strategy: {} and capital: {}", strategy, capital);
      tradeService.setStrategy(strategy);
      portfolio.setTotalCapital(capital);

      // Initialize the WebSocket client with Spring-managed dependencies
      KrakenWebSocketClient.initialize(tradeService, responseConverterUtils, krakenApiService,
          coinPairs);

      // Connect to WebSocket server
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      for (String coinPair : coinPairs) {
        var session = container.connectToServer(
            new SinglePairWebSocketClient(coinPair),
            URI.create(WS_URL)
        );
        activeSessions.add(session);
        log.info("WebSocket client started and connected for coin pair: {}", coinPair);
      }

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
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
    log.info("All WebSocket connections closed");
  }

}