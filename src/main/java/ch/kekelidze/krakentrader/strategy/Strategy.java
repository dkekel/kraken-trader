package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import org.ta4j.core.Bar;

public interface Strategy {

  boolean shouldBuy(List<Bar> data, StrategyParameters params);

  boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params);
}
