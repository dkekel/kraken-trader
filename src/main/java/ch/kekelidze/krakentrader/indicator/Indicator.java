package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.settings.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;

public interface Indicator {

  boolean isBuySignal(EvaluationContext evaluationContext, StrategyParameters params);

  boolean isSellSignal(EvaluationContext evaluationContext, double entryPrice,
      StrategyParameters params);
}
