package ch.kekelidze.krakentrader.indicator.optimize;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.optimize.model.LSTMModel;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class WalkForwardOptimizer implements Optimizer {

  private final GeneticOptimizer geneticOptimizer;
  private final LSTMModel lstmModel;

  @Override
  public StrategyParameters optimizeParameters(EvaluationContext context) {
    var data = context.getBars();
    var trainingData = getTestData(data);
    var evaluationContext = EvaluationContext.builder().symbol(context.getSymbol())
        .bars(trainingData).build();
    return geneticOptimizer.optimizeParameters(evaluationContext);
  }

  @Override
  public MultiLayerNetwork trainModel(List<Bar> data) {
    var trainingData = getTestData(data);
    return lstmModel.trainModel(trainingData);
  }

  /**
   * 70% training data
   */
  private List<Bar> getTestData(List<Bar> data) {
    int trainingSize = (int) (data.size() * 0.7);
    return data.subList(0, trainingSize);
  }
}