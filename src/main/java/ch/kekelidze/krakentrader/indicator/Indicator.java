package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import org.ta4j.core.Bar;

public interface Indicator {

  boolean isBuySignal(List<Bar> data, StrategyParameters params);

  boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params);
}
