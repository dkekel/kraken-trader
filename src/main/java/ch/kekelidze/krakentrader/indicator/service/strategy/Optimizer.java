package ch.kekelidze.krakentrader.indicator.service.strategy;

import ch.kekelidze.krakentrader.indicator.service.strategy.configuration.StrategyParameters;
import java.util.List;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.ta4j.core.Bar;

public interface Optimizer {

  StrategyParameters optimizeParameters(List<Bar> data);

  default MultiLayerNetwork trainModel(List<Bar> data) {
    return null;
  };
}
