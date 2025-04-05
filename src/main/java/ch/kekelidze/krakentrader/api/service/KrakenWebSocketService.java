package ch.kekelidze.krakentrader.api.service;

import ch.kekelidze.krakentrader.api.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.trade.TradeState;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KrakenWebSocketService {

  private static final String WS_URL = "wss://ws.kraken.com/v2";

  private final TradeState tradeState;

  private final TradeService tradeService;
  private final ResponseConverterUtils responseConverterUtils;
  private final KrakenApiService krakenApiService;
  
  public void startWebSocketClient() {
    try {
      // Initialize the WebSocket client with Spring-managed dependencies
      KrakenWebSocketClient.initialize(tradeService, responseConverterUtils, krakenApiService);
      tradeState.reset();

      // Connect to WebSocket server
      WebSocketContainer container = ContainerProvider.getWebSocketContainer();
      try (var session = container.connectToServer(KrakenWebSocketClient.class,
          URI.create(WS_URL))) {
        log.info("WebSocket client started and connected");
        Thread.currentThread().join();
      }
      
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  public static StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(9).movingAverageLongPeriod(21)
        .rsiBuyThreshold(30).rsiSellThreshold(70).rsiPeriod(14)
        .macdShortBarCount(12).macdLongBarCount(26).macdBarCount(9)
        .adxPeriod(14).adxBullishThreshold(25).adxBearishThreshold(30)
        .lossPercent(5).profitPercent(10)
        .volumePeriod(20)
        .aboveAverageThreshold(20)
        .weightedAgreementThreshold(55)
        .build();
  }
}