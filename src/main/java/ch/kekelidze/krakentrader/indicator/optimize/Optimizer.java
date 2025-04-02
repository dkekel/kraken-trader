package ch.kekelidze.krakentrader.indicator.optimize;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.ta4j.core.Bar;

public interface Optimizer {

  StrategyParameters optimizeParameters(List<Bar> data);

  default MultiLayerNetwork trainModel(List<Bar> data) {
    return null;
  };
}
