package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import org.ta4j.core.Bar;

public interface Indicator {

  boolean isBuySignal(EvaluationContext evaluationContext, StrategyParameters params);

  boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params);
}
