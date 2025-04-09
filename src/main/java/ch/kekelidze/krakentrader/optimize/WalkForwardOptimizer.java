package ch.kekelidze.krakentrader.optimize;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.optimize.util.TestDataUtils;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WalkForwardOptimizer implements Optimizer {

  private final GeneticOptimizer geneticOptimizer;
  private final TestDataUtils testDataUtils;

  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    var data = context.getBars();
    var trainingData = testDataUtils.getTestData(data);
    var evaluationContext = EvaluationContext.builder().symbol(context.getSymbol())
        .bars(trainingData).build();
    return geneticOptimizer.optimizeParameters(evaluationContext);
  }
}