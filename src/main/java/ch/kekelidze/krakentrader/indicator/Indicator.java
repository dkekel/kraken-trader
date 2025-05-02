package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;

public interface Indicator {

  boolean isBuySignal(EvaluationContext evaluationContext, StrategyParameters params);

  boolean isSellSignal(EvaluationContext context, double entryPrice, StrategyParameters params);
}
