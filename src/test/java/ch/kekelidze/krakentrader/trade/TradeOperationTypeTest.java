package ch.kekelidze.krakentrader.trade;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TradeOperationType enum
 */
public class TradeOperationTypeTest {

    @Test
    void toLowerCase_shouldReturnLowercaseValue() {
        // Test BUY
        assertEquals("buy", TradeOperationType.BUY.toLowerCase());
        
        // Test SELL
        assertEquals("sell", TradeOperationType.SELL.toLowerCase());
    }

    @Test
    void toString_shouldReturnEnumName() {
        // Test BUY
        assertEquals("BUY", TradeOperationType.BUY.toString());
        
        // Test SELL
        assertEquals("SELL", TradeOperationType.SELL.toString());
    }

    @Test
    void valueOf_shouldReturnCorrectEnum() {
        // Test BUY
        assertSame(TradeOperationType.BUY, TradeOperationType.valueOf("BUY"));
        
        // Test SELL
        assertSame(TradeOperationType.SELL, TradeOperationType.valueOf("SELL"));
    }

    @Test
    void valueOf_shouldThrowException_whenInvalidValue() {
        // Test invalid value
        assertThrows(IllegalArgumentException.class, () -> TradeOperationType.valueOf("INVALID"));
    }

    @Test
    void values_shouldReturnAllEnumValues() {
        // Test values
        TradeOperationType[] values = TradeOperationType.values();
        assertEquals(2, values.length);
        assertSame(TradeOperationType.BUY, values[0]);
        assertSame(TradeOperationType.SELL, values[1]);
    }
}