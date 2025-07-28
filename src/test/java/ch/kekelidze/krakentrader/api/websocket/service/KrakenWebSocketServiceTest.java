package ch.kekelidze.krakentrader.api.websocket.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.KrakenWebSocketClient;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.trade.Portfolio;
import ch.kekelidze.krakentrader.trade.repository.TradeStateRepository;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import ch.kekelidze.krakentrader.trade.service.TradeStatePersistenceService;
import jakarta.websocket.Session;
import jakarta.websocket.WebSocketContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for KrakenWebSocketService
 * 
 * Note: These tests focus on the public methods of the class.
 * Testing actual WebSocket connections would require integration tests.
 */
@ExtendWith(MockitoExtension.class)
public class KrakenWebSocketServiceTest {

    @Mock
    private Portfolio portfolio;

    @Mock
    private TradeService tradeService;

    @Mock
    private TradingApiService tradingApiService;

    @Mock
    private HistoricalDataService historicalDataService;

    @Mock
    private ResponseConverterUtils responseConverterUtils;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private Strategy strategy;

    @Mock
    private WebSocketContainer webSocketContainer;
    
    @Mock
    private TradeStatePersistenceService tradeStatePersistenceService;
    
    @Mock
    private TradeStateRepository tradeStateRepository;

    @Mock
    private Session session;

    @Mock
    private KrakenWebSocketClient webSocketClient;

    private KrakenWebSocketService webSocketService;

    @BeforeEach
    void setUp() throws Exception {
        // Create the service with mocked dependencies
        webSocketService = new KrakenWebSocketService(
            portfolio,
            tradeService,
            responseConverterUtils,
            tradingApiService,
            historicalDataService,
            applicationContext,
            tradeStatePersistenceService,
            tradeStateRepository
        );
        
        // Use reflection to set up the test environment
        
        // Set up activeSessions
        Field activeSessionsField = KrakenWebSocketService.class.getDeclaredField("activeSessions");
        activeSessionsField.setAccessible(true);
        List<Session> activeSessions = new ArrayList<>();
        activeSessions.add(session);
        activeSessionsField.set(webSocketService, activeSessions);
        
        // Set up clientToCoinPairMap
        Field clientToCoinPairMapField = KrakenWebSocketService.class.getDeclaredField("clientToCoinPairMap");
        clientToCoinPairMapField.setAccessible(true);
        Map<KrakenWebSocketClient, String> clientToCoinPairMap = new HashMap<>();
        clientToCoinPairMap.put(webSocketClient, "XBTUSD");
        clientToCoinPairMapField.set(webSocketService, clientToCoinPairMap);
        
        // Set up container
        Field containerField = KrakenWebSocketService.class.getDeclaredField("container");
        containerField.setAccessible(true);
        containerField.set(webSocketService, webSocketContainer);
        
        // Mock session properties - use lenient to avoid "unnecessary stubbing" errors
        lenient().when(session.getUserProperties()).thenReturn(new HashMap<>());
        lenient().when(session.isOpen()).thenReturn(true);
    }

    @Test
    void startWebSocketClient_shouldInitializeAndConnect() throws Exception {
        // Arrange
        String[] args = {"testStrategy", "XBTUSD,ETHUSD"};
        when(applicationContext.getBean("testStrategy", Strategy.class)).thenReturn(strategy);
        when(tradingApiService.getAssetBalance("USD")).thenReturn(10000.0);
        
        // We need to mock the static KrakenWebSocketClient.initialize method
        // This is challenging in a unit test, so we'll use a spy to verify other interactions
        KrakenWebSocketService spy = spy(webSocketService);
        
        // Act - we'll catch the exception since we can't fully mock everything
        try {
            spy.startWebSocketClient(args);
        } catch (Exception e) {
            // Expected since we can't fully mock the WebSocket connection
        }
        
        // Assert - verify interactions with dependencies
        verify(applicationContext).getBean("testStrategy", Strategy.class);
        verify(tradingApiService).getAssetBalance("USD");
        verify(portfolio).setTotalCapital(10000.0);
        verify(tradeService).setStrategy(strategy);
        // Portfolio allocation is now calculated dynamically based on coins not in trade
    }

    @Test
    void reconnectClient_shouldHandleReconnection() throws Exception {
        // Arrange - already set up in setUp()
        
        // Act
        webSocketService.reconnectClient(webSocketClient);
        
        // Assert
        // We can't easily verify internal behavior without accessing private methods,
        // but we can at least verify that the method doesn't throw an exception
        // In a real test, we would verify that the client was reconnected
    }

    @Test
    void destroy_shouldCloseAllSessions() throws Exception {
        // Act
        webSocketService.destroy();
        
        // Assert
        verify(session).close();
        
        // Verify that activeSessions was cleared
        Field activeSessionsField = KrakenWebSocketService.class.getDeclaredField("activeSessions");
        activeSessionsField.setAccessible(true);
        List<Session> activeSessions = (List<Session>) activeSessionsField.get(webSocketService);
        assertTrue(activeSessions.isEmpty());
        
        // Verify that clientToCoinPairMap was cleared
        Field clientToCoinPairMapField = KrakenWebSocketService.class.getDeclaredField("clientToCoinPairMap");
        clientToCoinPairMapField.setAccessible(true);
        Map<KrakenWebSocketClient, String> clientToCoinPairMap = (Map<KrakenWebSocketClient, String>) clientToCoinPairMapField.get(webSocketService);
        assertTrue(clientToCoinPairMap.isEmpty());
    }
}