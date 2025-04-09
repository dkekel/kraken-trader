package ch.kekelidze.krakentrader.indicator.optimize;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;

public interface Optimizer {

  StrategyParameters optimizeParameters(EvaluationContext context);
}
