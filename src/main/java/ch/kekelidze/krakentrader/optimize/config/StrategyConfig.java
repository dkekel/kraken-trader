package ch.kekelidze.krakentrader.optimize.config;

import ch.kekelidze.krakentrader.optimize.util.StrategySelector;
import ch.kekelidze.krakentrader.strategy.Strategy;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StrategyConfig {

  private final Strategy defaultStrategy;

  public StrategyConfig(@Qualifier("supportResistanceConsolidation") Strategy defaultStrategy) {
    this.defaultStrategy = defaultStrategy;
  }

  @Bean
  public StrategySelector strategySelector(List<Strategy> allStrategies) {
    // Convert list of strategies to a map with their bean names
    Map<String, Strategy> strategiesMap = allStrategies.stream()
        .collect(Collectors.toMap(
            strategy -> strategy.getClass()
                .getAnnotation(org.springframework.stereotype.Component.class).value(),
            Function.identity()
        ));

    return new StrategySelector(strategiesMap, defaultStrategy);
  }
}