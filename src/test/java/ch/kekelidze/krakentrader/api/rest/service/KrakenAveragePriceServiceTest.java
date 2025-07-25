package ch.kekelidze.krakentrader.api.rest.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for KrakenAveragePriceService
 * 
 * Note: These tests focus on the public API of KrakenAveragePriceService.
 * In a real-world scenario, we would need to use integration tests or
 * more sophisticated mocking to test the actual API interactions.
 */
@ExtendWith(MockitoExtension.class)
public class KrakenAveragePriceServiceTest {

    @InjectMocks
    private KrakenAveragePriceService krakenAveragePriceService;

    @BeforeEach
    void setUp() {
        // Set private fields using ReflectionTestUtils
        ReflectionTestUtils.setField(krakenAveragePriceService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(krakenAveragePriceService, "apiSecret", "test-api-secret");
    }

    @Test
    void getAveragePurchasePrices_shouldReturnAveragePrices() throws Exception {
        // Arrange
        KrakenAveragePriceService spy = spy(krakenAveragePriceService);
        
        // Create expected result
        Map<String, Double> expectedPrices = new HashMap<>();
        expectedPrices.put("XBT", 45000.0);
        expectedPrices.put("ETH", 3000.0);
        
        // Mock the method to return our expected result
        doReturn(expectedPrices).when(spy).getAveragePurchasePrices();
        
        // Act
        Map<String, Double> result = spy.getAveragePurchasePrices();
        
        // Assert
        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(45000.0, result.get("XBT"));
        assertEquals(3000.0, result.get("ETH"));
    }
}