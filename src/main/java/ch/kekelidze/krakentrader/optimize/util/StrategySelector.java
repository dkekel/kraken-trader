package ch.kekelidze.krakentrader.optimize.util;

import ch.kekelidze.krakentrader.strategy.Strategy;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StrategySelector {

  private final Map<String, Strategy> strategies;
  private final Strategy defaultStrategy;

  // Map to store best strategy per coin pair
  private final Map<String, String> bestStrategyPerCoin = new HashMap<>();

  public Strategy getStrategy(String strategyName) {
    if (!strategies.containsKey(strategyName)) {
      throw new IllegalArgumentException("Strategy not found: " + strategyName);
    }
    return strategies.get(strategyName);
  }

  public Strategy getBestStrategyForCoin(String coinPair) {
    var defaultStrategyName = defaultStrategy.getClass()
        .getAnnotation(org.springframework.stereotype.Component.class).value();
    String strategyName = bestStrategyPerCoin.getOrDefault(coinPair, defaultStrategyName);
    return getStrategy(strategyName);
  }

  public void setBestStrategyForCoin(String coinPair, String strategyName) {
    bestStrategyPerCoin.put(coinPair, strategyName);
  }

  public Map<String, String> getBestStrategiesMap() {
    return new HashMap<>(bestStrategyPerCoin);
  }
}