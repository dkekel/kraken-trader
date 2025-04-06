package ch.kekelidze.krakentrader.trade;

import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

@Component
@Getter
public class Portfolio {

  private final ConcurrentHashMap<String, TradeState> tradeStates = new ConcurrentHashMap<>();
  private final AtomicReference<Double> totalCapital = new AtomicReference<>(0.0);

  public TradeState getOrCreateTradeState(String coinPair) {
    return tradeStates.computeIfAbsent(coinPair, TradeState::new);
  }

  public double getTotalCapital() {
    return totalCapital.get();
  }

  public void setTotalCapital(double value) {
    totalCapital.set(value);
  }

  public double addToTotalCapital(double amount) {
    return totalCapital.updateAndGet(current -> current + amount);
  }
}
