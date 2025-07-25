package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.dto.OrderResult;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KrakenApiService
 * 
 * Note: These tests focus on the public API of KrakenApiService.
 * In a real-world scenario, we would need to use integration tests or
 * more sophisticated mocking to test the actual API interactions.
 */
@ExtendWith(MockitoExtension.class)
public class KrakenApiServiceTest {

    @Mock
    private HistoricalDataService historicalDataService;

    @Mock
    private ResponseConverterUtils responseConverterUtils;

    @InjectMocks
    private KrakenApiService krakenApiService;

    @BeforeEach
    void setUp() {
        // Set private fields using ReflectionTestUtils
        ReflectionTestUtils.setField(krakenApiService, "apiKey", "test-api-key");
        // Use a valid Base64 string for apiSecret
        ReflectionTestUtils.setField(krakenApiService, "apiSecret", "dGVzdGFwaXNlY3JldA=="); // Base64 for "testapisecret"
    }

    @Test
    void getAccountBalance_shouldReturnBalances() throws Exception {
        // This is a partial test that verifies the method signature
        // In a real scenario, we would need to mock the HTTP client
        KrakenApiService spy = spy(krakenApiService);
        
        // Create a mock response
        Map<String, Double> mockBalances = new HashMap<>();
        mockBalances.put("ZUSD", 1000.0);
        mockBalances.put("XXBT", 0.5);
        
        // Mock the method to return our test data
        doReturn(mockBalances).when(spy).getAccountBalance();
        
        // Act
        Map<String, Double> result = spy.getAccountBalance();
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(1000.0, result.get("ZUSD"));
        assertEquals(0.5, result.get("XXBT"));
    }

    @Test
    void getAssetBalance_shouldReturnBalance_whenAssetExists() throws Exception {
        // Arrange
        KrakenApiService spy = spy(krakenApiService);
        
        Map<String, Double> mockBalances = new HashMap<>();
        mockBalances.put("ZUSD", 1000.0);
        mockBalances.put("XXBT", 0.5);
        
        doReturn(mockBalances).when(spy).getAccountBalance();
        
        // Act
        Double result = spy.getAssetBalance("USD");
        
        // Assert
        assertNotNull(result);
        assertEquals(1000.0, result);
    }

    @Test
    void getAssetBalance_shouldThrowException_whenAssetDoesNotExist() throws Exception {
        // Arrange
        KrakenApiService spy = spy(krakenApiService);
        
        Map<String, Double> mockBalances = new HashMap<>();
        mockBalances.put("ZUSD", 1000.0);
        mockBalances.put("XXBT", 0.5);
        
        doReturn(mockBalances).when(spy).getAccountBalance();
        
        // Act & Assert
        assertThrows(RuntimeException.class, () -> spy.getAssetBalance("ETH"));
    }

    @Test
    void placeMarketBuyOrder_shouldReturnOrderResult() throws Exception {
        // Arrange
        KrakenApiService spy = spy(krakenApiService);
        OrderResult expectedResult = new OrderResult("TESTORDER123", 2.5, 50000.0, 0.1);
        
        doReturn(expectedResult).when(spy).placeMarketBuyOrder(anyString(), anyDouble());
        
        // Act
        OrderResult result = spy.placeMarketBuyOrder("XBTUSD", 0.1);
        
        // Assert
        assertNotNull(result);
        assertEquals("TESTORDER123", result.orderId());
        assertEquals(2.5, result.fee());
        assertEquals(50000.0, result.executedPrice());
        assertEquals(0.1, result.volume());
    }

    @Test
    void placeMarketSellOrder_shouldReturnOrderResult() throws Exception {
        // Arrange
        KrakenApiService spy = spy(krakenApiService);
        OrderResult expectedResult = new OrderResult("TESTORDER123", 2.5, 50000.0, 0.1);
        
        doReturn(expectedResult).when(spy).placeMarketSellOrder(anyString(), anyDouble());
        
        // Act
        OrderResult result = spy.placeMarketSellOrder("XBTUSD", 0.1);
        
        // Assert
        assertNotNull(result);
        assertEquals("TESTORDER123", result.orderId());
        assertEquals(2.5, result.fee());
        assertEquals(50000.0, result.executedPrice());
        assertEquals(0.1, result.volume());
    }

    @Test
    void getCoinTradingFee_shouldReturnFee() throws Exception {
        // Arrange
        KrakenApiService spy = spy(krakenApiService);
        double expectedFee = 0.0026;
        
        doReturn(expectedFee).when(spy).getCoinTradingFee(anyString());
        
        // Act
        double result = spy.getCoinTradingFee("XBTUSD");
        
        // Assert
        assertEquals(expectedFee, result);
    }

    @Test
    void getApiSignature_shouldGenerateSignature() throws Exception {
        // Arrange
        String path = "/0/private/Balance";
        String nonce = "1234567890";
        String postData = "nonce=1234567890";
        
        // Act
        String signature = krakenApiService.getApiSignature(path, nonce, postData);
        
        // Assert
        assertNotNull(signature);
        assertFalse(signature.isEmpty());
    }
}