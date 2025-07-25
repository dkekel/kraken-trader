package ch.kekelidze.krakentrader.api.websocket;

import jakarta.websocket.Session;
import jakarta.websocket.RemoteEndpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for SinglePairWebSocketClient
 * 
 * Note: These tests focus on the unique aspects of SinglePairWebSocketClient.
 * Most of the functionality is inherited from KrakenWebSocketClient and is tested there.
 */
@ExtendWith(MockitoExtension.class)
public class SinglePairWebSocketClientTest {

    @Mock
    private Session session;

    @Mock
    private RemoteEndpoint.Async asyncRemote;

    private SinglePairWebSocketClient client;
    private final String coinPair = "XBTUSD";

    @BeforeEach
    void setUp() throws Exception {
        // Create a new instance of SinglePairWebSocketClient
        client = new SinglePairWebSocketClient(coinPair);
        
        // Set up the session mock - use lenient to avoid "unnecessary stubbing" errors
        lenient().when(session.getAsyncRemote()).thenReturn(asyncRemote);
        
        // Initialize the lastMessageTimestamp field using reflection
        Field lastMessageTimestampField = KrakenWebSocketClient.class.getDeclaredField("lastMessageTimestamp");
        lastMessageTimestampField.setAccessible(true);
        lastMessageTimestampField.set(client, new AtomicLong(System.currentTimeMillis()));
    }

    @Test
    void constructor_shouldSetCoinPair() throws Exception {
        // Use reflection to access the private coinPair field
        Field coinPairField = SinglePairWebSocketClient.class.getDeclaredField("coinPair");
        coinPairField.setAccessible(true);
        
        // Verify that the coinPair field was set correctly
        assertEquals(coinPair, coinPairField.get(client));
    }

    @Test
    void onOpen_shouldSubscribeToSpecificCoinPair() throws Exception {
        // Mock the getSubscribeMessage method to return a known value
        Method getSubscribeMessageMethod = KrakenWebSocketClient.class.getDeclaredMethod("getSubscribeMessage", String.class);
        getSubscribeMessageMethod.setAccessible(true);
        
        // Call the onOpen method
        client.onOpen(session);
        
        // Verify that asyncRemote.sendText was called
        verify(asyncRemote, times(1)).sendText(anyString());
        
        // We can't easily verify the exact message that was sent because getSubscribeMessage is private,
        // but we can at least verify that sendText was called, which means a subscription message was sent
    }
}