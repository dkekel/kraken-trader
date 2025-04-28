package ch.kekelidze.krakentrader.trade;

import ch.kekelidze.krakentrader.trade.service.PortfolioPersistenceService;
import ch.kekelidze.krakentrader.trade.service.TradeStatePersistenceService;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;

@Component
@Getter
@Slf4j
public class Portfolio {

  private final ConcurrentHashMap<String, TradeState> tradeStates = new ConcurrentHashMap<>();
  private final AtomicReference<Double> totalCapital = new AtomicReference<>(0.0);

  private final PortfolioPersistenceService portfolioPersistenceService;
  private final TradeStatePersistenceService tradeStatePersistenceService;

  @Autowired
  public Portfolio(PortfolioPersistenceService portfolioPersistenceService, 
                  TradeStatePersistenceService tradeStatePersistenceService) {
    this.portfolioPersistenceService = portfolioPersistenceService;
    this.tradeStatePersistenceService = tradeStatePersistenceService;
  }

  @PostConstruct
  public void init() {
    // Load total capital from database
    portfolioPersistenceService.loadPortfolioTotalCapital()
        .ifPresent(capital -> {
          totalCapital.set(capital);
          log.info("Loaded total capital from database: {}", capital);
        });
  }

  public TradeState getOrCreateTradeState(String coinPair) {
    return tradeStates.computeIfAbsent(coinPair, key -> {
      // Try to load from database first
      return tradeStatePersistenceService.loadTradeState(key)
          .orElseGet(() -> new TradeState(key));
    });
  }

  public double getTotalCapital() {
    return totalCapital.get();
  }

  public void setTotalCapital(double value) {
    totalCapital.set(value);
    portfolioPersistenceService.savePortfolioTotalCapital(value);
  }

  public double addToTotalCapital(double amount) {
    double newValue = totalCapital.updateAndGet(current -> current + amount);
    portfolioPersistenceService.savePortfolioTotalCapital(newValue);
    return newValue;
  }
}
