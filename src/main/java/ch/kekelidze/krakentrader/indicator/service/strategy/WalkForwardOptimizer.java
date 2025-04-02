package ch.kekelidze.krakentrader.indicator.service.strategy;

import ch.kekelidze.krakentrader.indicator.service.strategy.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.service.strategy.model.LSTMModel;
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
    public StrategyParameters optimizeParameters(List<Bar> data) {
       var trainingData = getTestData(data);
        return geneticOptimizer.optimizeParameters(trainingData);
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