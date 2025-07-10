package ch.kekelidze.krakentrader.trade.util;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class TradingCircuitBreaker {

  @Getter
  public enum CircuitState {
    CLOSED,    // Normal trading
    OPEN,      // Trading halted
    HALF_OPEN  // Testing if conditions have improved
  }

  public static class CircuitBreakerState {

    @Getter
    private CircuitState state = CircuitState.CLOSED;
    @Getter
    private int consecutiveLosses = 0;
    @Getter
    private double totalLossPercent = 0.0;
    @Getter
    private LocalDateTime lastFailure;
    @Getter
    private LocalDateTime circuitOpenedAt;
    @Getter
    private int testTradesInHalfOpen = 0;

    public void recordLoss(double lossPercent) {
      consecutiveLosses++;
      totalLossPercent += Math.abs(lossPercent);
      lastFailure = LocalDateTime.now();
    }

    public void recordWin() {
      consecutiveLosses = 0;
      totalLossPercent = 0.0;
      testTradesInHalfOpen = 0;
    }

    public void openCircuit() {
      state = CircuitState.OPEN;
      circuitOpenedAt = LocalDateTime.now();
      log.warn("Circuit breaker OPENED - trading halted due to excessive losses");
    }

    public void closeCircuit() {
      state = CircuitState.CLOSED;
      consecutiveLosses = 0;
      totalLossPercent = 0.0;
      circuitOpenedAt = null;
      testTradesInHalfOpen = 0;
      log.info("Circuit breaker CLOSED - normal trading resumed");
    }

    public void setHalfOpen() {
      state = CircuitState.HALF_OPEN;
      testTradesInHalfOpen = 0;
      log.info("Circuit breaker HALF-OPEN - testing market conditions");
    }

    public void incrementTestTrades() {
      testTradesInHalfOpen++;
    }
  }

  private final Map<String, CircuitBreakerState> coinPairStates = new ConcurrentHashMap<>();

  @Value("${trading.circuit-breaker.max-consecutive-losses}")
  private int maxConsecutiveLosses;

  @Value("${trading.circuit-breaker.max-loss-percent-in-period}")
  private double maxLossPercentInPeriod;

  @Value("${trading.circuit-breaker.circuit-open-minutes}")
  private long circuitOpenMinutes;

  @Value("${trading.circuit-breaker.test-trades-in-half-open}")
  private int testTradesInHalfOpen;

  @PostConstruct
  public void logConfiguration() {
    log.info("Circuit Breaker Configuration:");
    log.info("  - Max consecutive losses: {}", maxConsecutiveLosses);
    log.info("  - Max loss percent in period: {}%", maxLossPercentInPeriod);
    log.info("  - Circuit open duration: {} minutes", circuitOpenMinutes);
    log.info("  - Test trades in half-open: {}", testTradesInHalfOpen);

    if (maxConsecutiveLosses <= 2) {
      log.warn("Very aggressive circuit breaker - will trigger after only {} losses",
          maxConsecutiveLosses);
    }
    if (maxLossPercentInPeriod >= 20.0) {
      log.warn("High loss threshold ({}%) - consider lowering for better protection",
          maxLossPercentInPeriod);
    }
  }

  /**
   * Checks if trading is allowed for the given coin pair
   */
  public boolean canTrade(String coinPair) {
    CircuitBreakerState state = getOrCreateState(coinPair);

    return switch (state.getState()) {
      case CLOSED -> true;
      case OPEN -> {
        // Check if enough time has passed to try half-open
        if (shouldTransitionToHalfOpen(state)) {
          state.setHalfOpen();
          yield true;
        }
        yield false;
      }
      case HALF_OPEN ->
        // Allow limited test trades
        state.getTestTradesInHalfOpen() < testTradesInHalfOpen;
    };
  }

  /**
   * Records the result of a trade and updates circuit breaker state
   */
  public void recordTradeResult(String coinPair, double profitPercent) {
    CircuitBreakerState state = getOrCreateState(coinPair);

    if (profitPercent < 0) {
      // Loss recorded
      state.recordLoss(profitPercent);

      if (state.getState() == CircuitState.HALF_OPEN) {
        // Failed test trade - back to open
        state.openCircuit();
        log.warn("Test trade failed for {} - circuit breaker reopened", coinPair);
      } else {
        // Check if we should open the circuit
        if (shouldOpenCircuit(state)) {
          state.openCircuit();
        }
      }
    } else {
      // Profit recorded
      if (state.getState() == CircuitState.HALF_OPEN) {
        state.incrementTestTrades();
        // If we've had enough successful test trades, close circuit
        if (state.getTestTradesInHalfOpen() >= testTradesInHalfOpen) {
          state.closeCircuit();
          log.info("Test trades successful for {} - circuit breaker fully closed", coinPair);
        }
      } else {
        state.recordWin();
      }
    }

    logCircuitState(coinPair, state, profitPercent);
  }

  /**
   * Get current circuit state for a coin pair
   */
  public CircuitState getCircuitState(String coinPair) {
    return getOrCreateState(coinPair).getState();
  }

  /**
   * Get detailed state information for monitoring
   */
  public CircuitBreakerState getDetailedState(String coinPair) {
    return getOrCreateState(coinPair);
  }

  /**
   * Manually reset circuit breaker (for emergency situations)
   */
  public void resetCircuitBreaker(String coinPair) {
    CircuitBreakerState state = getOrCreateState(coinPair);
    state.closeCircuit();
    log.warn("Circuit breaker manually reset for {}", coinPair);
  }

  private CircuitBreakerState getOrCreateState(String coinPair) {
    return coinPairStates.computeIfAbsent(coinPair, k -> new CircuitBreakerState());
  }

  private boolean shouldOpenCircuit(CircuitBreakerState state) {
    return state.getConsecutiveLosses() >= maxConsecutiveLosses ||
        state.getTotalLossPercent() >= maxLossPercentInPeriod;
  }

  private boolean shouldTransitionToHalfOpen(CircuitBreakerState state) {
    if (state.getCircuitOpenedAt() == null) {
      return false;
    }

    long minutesSinceOpened = ChronoUnit.MINUTES.between(
        state.getCircuitOpenedAt(),
        LocalDateTime.now()
    );

    return minutesSinceOpened >= circuitOpenMinutes;
  }

  private void logCircuitState(String coinPair, CircuitBreakerState state, double profitPercent) {
    log.debug(
        "Circuit state for {}: {} | Consecutive losses: {} | Total loss: {}% | Last result: {}%",
        coinPair,
        state.getState(),
        state.getConsecutiveLosses(),
        String.format("%.2f", state.getTotalLossPercent()),
        String.format("%.2f", profitPercent));
  }
}