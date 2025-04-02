package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import org.ta4j.core.Bar;

public interface Strategy {

  boolean isBuyTrigger(List<Bar> data, StrategyParameters params);

  boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params);
}
