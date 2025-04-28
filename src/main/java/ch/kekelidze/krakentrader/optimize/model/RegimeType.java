package ch.kekelidze.krakentrader.optimize.model;

/**
 * Enum representing different market regime types with more detailed classifications
 */
public enum RegimeType {
  VOLATILE_UPTREND,    // High volatility with strong upward trend
  VOLATILE_DOWNTREND,  // High volatility with strong downward trend
  VOLATILE_RANGING,    // High volatility but primarily range-bound
  NORMAL_UPTREND,      // Normal volatility with moderate upward trend
  NORMAL_DOWNTREND,    // Normal volatility with moderate downward trend
  NORMAL_RANGING,      // Normal volatility with sideways movement
  CALM_UPTREND,        // Low volatility with mild upward trend
  CALM_DOWNTREND,      // Low volatility with mild downward trend
  CALM_RANGING         // Low volatility, very tight range
}
