package ch.kekelidze.krakentrader.api.websocket;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.api.websocket.service.KrakenWebSocketService;
import ch.kekelidze.krakentrader.trade.service.TradeService;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for KrakenWebSocketClient
 * 
 * Note: These tests focus on the non-WebSocket aspects of the class.
 * Testing actual WebSocket connections would require integration tests.
 */
@ExtendWith(MockitoExtension.class)
public class KrakenWebSocketClientTest {

    @Mock
    private TradeService tradeService;

    @Mock
    private ResponseConverterUtils responseConverterUtils;

    @Mock
    private HistoricalDataService marketDataService;

    @Mock
    private KrakenWebSocketService webSocketService;

    @Mock
    private Session session;

    private KrakenWebSocketClient client;
    private Bar mockBar;

    @BeforeEach
    void setUp() throws Exception {
        // Create a new instance of KrakenWebSocketClient
        client = new KrakenWebSocketClient();
        
        // Create a mock Bar for testing
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        mockBar = BaseBar.builder()
                .timePeriod(Duration.ofHours(1))
                .endTime(now)
                .openPrice(DecimalNum.valueOf(40000.0))
                .highPrice(DecimalNum.valueOf(41000.0))
                .lowPrice(DecimalNum.valueOf(39000.0))
                .closePrice(DecimalNum.valueOf(40000.0))
                .volume(DecimalNum.valueOf(10.0))
                .build();
        
        // Initialize the client with mocked dependencies using reflection
        // This is necessary because the initialize method is static and sets static fields
        try (MockedStatic<KrakenWebSocketClient> mockedStatic = mockStatic(KrakenWebSocketClient.class, invocation -> {
            Method method = invocation.getMethod();
            if (method.getName().equals("initialize")) {
                // Set the static fields using reflection
                setStaticField(KrakenWebSocketClient.class, "tradeService", tradeService);
                setStaticField(KrakenWebSocketClient.class, "responseConverterUtils", responseConverterUtils);
                setStaticField(KrakenWebSocketClient.class, "webSocketService", webSocketService);
                setStaticField(KrakenWebSocketClient.class, "SYMBOLS", List.of("XBTUSD"));
                
                // Get the existing priceQueue map and modify it
                Field priceQueueField = KrakenWebSocketClient.class.getDeclaredField("priceQueue");
                priceQueueField.setAccessible(true);
                Map<String, Deque<Bar>> priceQueue = (Map<String, Deque<Bar>>) priceQueueField.get(null);
                
                // Clear the map and add our test data
                priceQueue.clear();
                Deque<Bar> queue = new LinkedList<>();
                queue.add(mockBar);
                priceQueue.put("XBTUSD", queue);
                
                return null;
            }
            return invocation.callRealMethod();
        })) {
            // Call the initialize method
            KrakenWebSocketClient.initialize(tradeService, responseConverterUtils, marketDataService, new String[]{"XBTUSD"}, webSocketService);
        }
    }

    @Test
    void getSubscribeMessage_shouldReturnValidJson() throws Exception {
        // Use reflection to access the private method
        Method method = KrakenWebSocketClient.class.getDeclaredMethod("getSubscribeMessage", String.class);
        method.setAccessible(true);
        
        // Call the method
        String result = (String) method.invoke(client, "XBTUSD");
        
        // Verify the result is valid JSON and has the expected structure
        JSONObject json = new JSONObject(result);
        assertEquals("subscribe", json.getString("method"));
        
        JSONObject params = json.getJSONObject("params");
        assertEquals("ohlc", params.getString("channel"));
        
        JSONArray symbols = params.getJSONArray("symbol");
        assertEquals(1, symbols.length());
        assertEquals("XBTUSD", symbols.getString(0));
        
        assertEquals(60, params.getInt("interval"));
    }

    @Test
    void onOpen_shouldSendSubscribeMessage() throws Exception {
        // Use reflection to access the private method
        Method method = KrakenWebSocketClient.class.getDeclaredMethod("onOpen", Session.class);
        method.setAccessible(true);
        
        // Mock the session and async remote
        Session mockSession = mock(Session.class);
        RemoteEndpoint.Async mockAsyncRemote = mock(RemoteEndpoint.Async.class);
        when(mockSession.getAsyncRemote()).thenReturn(mockAsyncRemote);
        
        // Call the method
        method.invoke(client, mockSession);
        
        // Verify that the session.getAsyncRemote().sendText() was called
        verify(mockAsyncRemote, times(1)).sendText(anyString());
    }

    @Test
    void isUpdateMessage_shouldReturnTrueForUpdateMessage() throws Exception {
        // Use reflection to access the private method
        Method method = KrakenWebSocketClient.class.getDeclaredMethod("isUpdateMessage", JSONObject.class);
        method.setAccessible(true);
        
        // Create a JSON object that looks like an update message
        JSONObject json = new JSONObject();
        json.put("type", "update");
        
        // Call the method
        boolean result = (boolean) method.invoke(client, json);
        
        // Verify the result
        assertTrue(result);
    }

    @Test
    void isUpdateMessage_shouldReturnFalseForNonUpdateMessage() throws Exception {
        // Use reflection to access the private method
        Method method = KrakenWebSocketClient.class.getDeclaredMethod("isUpdateMessage", JSONObject.class);
        method.setAccessible(true);
        
        // Create a JSON object that doesn't look like an update message
        JSONObject json = new JSONObject();
        json.put("event", "subscriptionStatus");
        
        // Call the method
        boolean result = (boolean) method.invoke(client, json);
        
        // Verify the result
        assertFalse(result);
    }

    @Test
    void enqueueNewBar_shouldAddBarToQueue() throws Exception {
        // Use reflection to access the private method
        Method method = KrakenWebSocketClient.class.getDeclaredMethod("enqueueNewBar", Bar.class, Deque.class);
        method.setAccessible(true);
        
        // Create a queue and a new bar
        Deque<Bar> queue = new LinkedList<>();
        Bar newBar = BaseBar.builder()
                .timePeriod(Duration.ofHours(1))
                .endTime(ZonedDateTime.now(ZoneId.systemDefault()))
                .openPrice(DecimalNum.valueOf(41000.0))
                .highPrice(DecimalNum.valueOf(42000.0))
                .lowPrice(DecimalNum.valueOf(40000.0))
                .closePrice(DecimalNum.valueOf(41500.0))
                .volume(DecimalNum.valueOf(5.0))
                .build();
        
        // Call the method
        method.invoke(client, newBar, queue);
        
        // Verify the result
        assertEquals(1, queue.size());
        assertEquals(newBar, queue.getFirst());
    }

    @Test
    void onError_shouldLogError() throws Exception {
        // Use reflection to access the private method
        Method onErrorMethod = KrakenWebSocketClient.class.getDeclaredMethod("onError", Session.class, Throwable.class);
        onErrorMethod.setAccessible(true);
        
        // Create a throwable
        Throwable throwable = new IOException("Test error");
        
        // Call the method - we can't verify that requestReconnection was called
        // because it's a private static method, but we can at least verify that
        // the method doesn't throw an exception
        onErrorMethod.invoke(client, session, throwable);
        
        // No assertion needed - we're just verifying that the method doesn't throw an exception
    }

    /**
     * Helper method to set a static field using reflection
     */
    private static void setStaticField(Class<?> clazz, String fieldName, Object value) throws Exception {
        Field field = clazz.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, value);
    }
}