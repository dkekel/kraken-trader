package ch.kekelidze.krakentrader.trade;

import ch.kekelidze.krakentrader.trade.service.PortfolioPersistenceService;
import ch.kekelidze.krakentrader.trade.service.TradeStatePersistenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Tests for Portfolio class
 */
@ExtendWith(MockitoExtension.class)
public class PortfolioTest {

    @Mock
    private PortfolioPersistenceService portfolioPersistenceService;

    @Mock
    private TradeStatePersistenceService tradeStatePersistenceService;

    private Portfolio portfolio;

    @BeforeEach
    void setUp() {
        portfolio = new Portfolio(portfolioPersistenceService, tradeStatePersistenceService);
    }

    @Test
    void init_shouldLoadTotalCapitalFromDatabase() {
        // Arrange
        double expectedCapital = 10000.0;
        when(portfolioPersistenceService.loadPortfolioTotalCapital()).thenReturn(Optional.of(expectedCapital));

        // Act
        portfolio.init();

        // Assert
        assertEquals(expectedCapital, portfolio.getTotalCapital());
        verify(portfolioPersistenceService).loadPortfolioTotalCapital();
    }

    @Test
    void init_shouldNotSetTotalCapital_whenDatabaseHasNoValue() {
        // Arrange
        when(portfolioPersistenceService.loadPortfolioTotalCapital()).thenReturn(Optional.empty());

        // Act
        portfolio.init();

        // Assert
        assertEquals(0.0, portfolio.getTotalCapital());
        verify(portfolioPersistenceService).loadPortfolioTotalCapital();
    }

    @Test
    void getOrCreateTradeState_shouldReturnExistingTradeState_whenItExists() {
        // Arrange
        String coinPair = "XBTUSD";
        TradeState existingState = new TradeState(coinPair);
        
        // Add the trade state to the portfolio
        portfolio.getTradeStates().put(coinPair, existingState);

        // Act
        TradeState result = portfolio.getOrCreateTradeState(coinPair);

        // Assert
        assertSame(existingState, result);
        verifyNoInteractions(tradeStatePersistenceService);
    }

    @Test
    void getOrCreateTradeState_shouldLoadFromDatabase_whenNotInMemory() {
        // Arrange
        String coinPair = "XBTUSD";
        TradeState databaseState = new TradeState(coinPair);
        when(tradeStatePersistenceService.loadTradeState(coinPair)).thenReturn(Optional.of(databaseState));

        // Act
        TradeState result = portfolio.getOrCreateTradeState(coinPair);

        // Assert
        assertSame(databaseState, result);
        verify(tradeStatePersistenceService).loadTradeState(coinPair);
    }

    @Test
    void getOrCreateTradeState_shouldCreateNew_whenNotInMemoryOrDatabase() {
        // Arrange
        String coinPair = "XBTUSD";
        when(tradeStatePersistenceService.loadTradeState(coinPair)).thenReturn(Optional.empty());

        // Act
        TradeState result = portfolio.getOrCreateTradeState(coinPair);

        // Assert
        assertNotNull(result);
        assertEquals(coinPair, result.getCoinPair());
        verify(tradeStatePersistenceService).loadTradeState(coinPair);
    }

    @Test
    void setTotalCapital_shouldUpdateAndPersistValue() {
        // Arrange
        double newCapital = 15000.0;

        // Act
        portfolio.setTotalCapital(newCapital);

        // Assert
        assertEquals(newCapital, portfolio.getTotalCapital());
        verify(portfolioPersistenceService).savePortfolioTotalCapital(newCapital);
    }

    @Test
    void addToTotalCapital_shouldAddAndPersistValue() {
        // Arrange
        double initialCapital = 10000.0;
        double amountToAdd = 5000.0;
        double expectedCapital = 15000.0;
        
        portfolio.setTotalCapital(initialCapital);
        // Clear invocations from the setTotalCapital call
        clearInvocations(portfolioPersistenceService);

        // Act
        double result = portfolio.addToTotalCapital(amountToAdd);

        // Assert
        assertEquals(expectedCapital, result);
        assertEquals(expectedCapital, portfolio.getTotalCapital());
        verify(portfolioPersistenceService).savePortfolioTotalCapital(expectedCapital);
    }
}