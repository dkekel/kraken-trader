package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.dto.OrderResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for PaperTradeKrakenApiService
 */
@ExtendWith(MockitoExtension.class)
public class PaperTradeKrakenApiServiceTest {

    @Mock
    private HistoricalDataService historicalDataService;

    private PaperTradeKrakenApiService paperTradeService;
    private Bar mockBar;
    private final double initialBalance = 10000.0;

    @BeforeEach
    void setUp() throws Exception {
        // Create the service with initial balance
        paperTradeService = new PaperTradeKrakenApiService(historicalDataService, initialBalance);
        
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
        
        // Setup mock historical data service to return our mock bar - use lenient to avoid "unnecessary stubbing" errors
        // since not all tests will use this mock
        lenient().when(historicalDataService.queryHistoricalData(anyList(), anyInt()))
                .thenReturn(Map.of("XBTUSD", List.of(mockBar)));
    }

    @Test
    void getAccountBalance_shouldReturnInitialBalance() {
        // Act
        Map<String, Double> balances = paperTradeService.getAccountBalance();
        
        // Assert
        assertNotNull(balances);
        assertEquals(1, balances.size());
        assertEquals(initialBalance, balances.get("USD"));
    }

    @Test
    void getAssetBalance_shouldReturnCorrectBalance() throws Exception {
        // Arrange - Buy some BTC first to have a balance
        paperTradeService.placeMarketBuyOrder("XBTUSD", 0.1);
        
        // Act
        Double usdBalance = paperTradeService.getAssetBalance("USD");
        Double btcBalance = paperTradeService.getAssetBalance("XBT");
        
        // Assert
        assertNotNull(usdBalance);
        assertNotNull(btcBalance);
        assertEquals(0.1, btcBalance, 0.0001);
        // USD balance should be reduced by the purchase amount plus fee
        double expectedUsdBalance = initialBalance - (0.1 * 40000.0) - (0.1 * 40000.0 * 0.0026);
        assertEquals(expectedUsdBalance, usdBalance, 0.01);
    }

    @Test
    void getCoinTradingFee_shouldReturnDefaultFee() {
        // Act
        double fee = paperTradeService.getCoinTradingFee("XBTUSD");
        
        // Assert
        assertEquals(0.26, fee);
    }

    @Test
    void placeMarketBuyOrder_shouldUpdateBalances() throws Exception {
        // Act
        OrderResult result = paperTradeService.placeMarketBuyOrder("XBTUSD", 0.1);
        
        // Assert
        assertNotNull(result);
        assertEquals(0.1, result.volume());
        assertEquals(40000.0, result.executedPrice());
        
        // Check balances were updated correctly
        Map<String, Double> balances = paperTradeService.getAccountBalance();
        assertEquals(0.1, balances.get("XBT"));
        
        // USD balance should be reduced by the purchase amount plus fee
        double expectedUsdBalance = initialBalance - (0.1 * 40000.0) - (0.1 * 40000.0 * 0.0026);
        assertEquals(expectedUsdBalance, balances.get("USD"), 0.01);
    }

    @Test
    void placeMarketBuyOrder_shouldThrowException_whenInsufficientFunds() {
        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            paperTradeService.placeMarketBuyOrder("XBTUSD", 1000.0); // Try to buy more than we can afford
        });
        
        assertTrue(exception.getMessage().contains("Insufficient funds"));
    }

    @Test
    void placeMarketSellOrder_shouldUpdateBalances() throws Exception {
        // Arrange - Buy some BTC first
        paperTradeService.placeMarketBuyOrder("XBTUSD", 0.1);
        
        // Act
        OrderResult result = paperTradeService.placeMarketSellOrder("XBTUSD", 0.05);
        
        // Assert
        assertNotNull(result);
        assertEquals(0.05, result.volume());
        assertEquals(40000.0, result.executedPrice());
        
        // Check balances were updated correctly
        Map<String, Double> balances = paperTradeService.getAccountBalance();
        assertEquals(0.05, balances.get("XBT")); // Should have 0.05 BTC left
        
        // Calculate expected USD balance
        double buyAmount = 0.1 * 40000.0;
        double buyFee = 0.1 * 40000.0 * 0.0026;
        double sellAmount = 0.05 * 40000.0;
        double sellFee = 0.05 * 40000.0 * 0.0026;
        double expectedUsdBalance = initialBalance - buyAmount - buyFee + sellAmount - sellFee;
        assertEquals(expectedUsdBalance, balances.get("USD"), 0.01);
    }

    @Test
    void placeMarketSellOrder_shouldThrowException_whenInsufficientAsset() {
        // Act & Assert
        Exception exception = assertThrows(Exception.class, () -> {
            paperTradeService.placeMarketSellOrder("XBTUSD", 0.1); // Try to sell BTC we don't have
        });
        
        assertTrue(exception.getMessage().contains("Insufficient asset"));
    }

    @Test
    void getApiSignature_shouldReturnEmptyString() {
        // Act
        String signature = paperTradeService.getApiSignature("path", "nonce", "postData");
        
        // Assert
        assertEquals("", signature);
    }
}