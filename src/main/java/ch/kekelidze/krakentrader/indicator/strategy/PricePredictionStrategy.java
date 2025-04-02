package ch.kekelidze.krakentrader.indicator.strategy;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

@Component
public class PricePredictionStrategy implements Strategy {

  private final MultiLayerNetwork model;
  
  public PricePredictionStrategy() throws IOException {
    var modelFile = new File("model.h5");
    if (!modelFile.exists()) {
      throw new RuntimeException("Model file does not exist!");
    }
    
    this.model = MultiLayerNetwork.load(modelFile, true);
  }

  @Override
  public boolean isBuyTrigger(List<Bar> data, StrategyParameters params) {
    var prediction = calculatePrediction(data);
    var previousPrice = data.get(data.size() - 2).getClosePrice().doubleValue();
    return prediction > previousPrice;
  }

  @Override
  public boolean isSellTrigger(List<Bar> data, double entryPrice, StrategyParameters params) {
    var prediction = calculatePrediction(data);
    var previousPrice = data.get(data.size() - 2).getClosePrice().doubleValue();
    return prediction < previousPrice;
  }
  
  private double calculatePrediction(List<Bar> data) {
    // Prepare input sequence (last 10 prices)
    var inputSequence = data.subList(data.size() - 10, data.size()).stream()
        .map(Bar::getClosePrice).map(Num::doubleValue).toArray(Double[]::new);
    double[] inputArray = new double[inputSequence.length];
    for (int j = 0; j < inputSequence.length; j++) {
      inputArray[j] = inputSequence[j];
    }

    // Reshape to [1, 1, timeSteps] for LSTM input
    INDArray input = Nd4j.create(inputArray, new int[]{1, 1, inputArray.length});
    INDArray output = model.output(input);
    return output.getDouble(0); 
  }
}
