package ch.kekelidze.krakentrader.trade;

/**
 * Enum representing the types of trading operations.
 * Used instead of string literals for better type safety and code readability.
 */
public enum TradeOperationType {
    BUY,
    SELL;

    /**
     * Returns the lowercase representation of the enum value.
     * This is useful for API calls that require lowercase strings.
     *
     * @return the lowercase string representation of this enum value
     */
    public String toLowerCase() {
        return this.name().toLowerCase();
    }

    /**
     * Returns the string representation of the enum value.
     * This is useful for logging and display purposes.
     *
     * @return the string representation of this enum value
     */
    @Override
    public String toString() {
        return this.name();
    }
}