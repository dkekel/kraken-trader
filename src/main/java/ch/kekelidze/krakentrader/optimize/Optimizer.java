package ch.kekelidze.krakentrader.optimize;

import ch.kekelidze.krakentrader.indicator.settings.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;

public interface Optimizer {

  StrategyParameters optimizeParameters(EvaluationContext context);
}
