package ch.kekelidze.krakentrader.indicator.service.strategy.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.LSTM;
import org.deeplearning4j.nn.conf.layers.RnnOutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

@Component
public class LSTMModel {
    
    private MultiLayerNetwork buildModel(int inputSize, int outputSize) {
        MultiLayerConfiguration config = new NeuralNetConfiguration.Builder()
            .seed(123)
            .updater(new Adam(0.001))
            .weightInit(WeightInit.XAVIER)
            .list()
            .layer(new LSTM.Builder()
                .nIn(inputSize)
                .nOut(64)
                .activation(Activation.TANH)
                .build())
            .layer(new RnnOutputLayer.Builder()
                .nIn(64)
                .nOut(outputSize)
                .activation(Activation.IDENTITY)
                .lossFunction(LossFunctions.LossFunction.MSE)
                .build())
            .build();

        MultiLayerNetwork model = new MultiLayerNetwork(config);
        model.init();
        return model;
    }

    public MultiLayerNetwork trainModel(List<Bar> historicalPrices) {
        // Convert prices to sequences (e.g., 10 time steps → predict next 1)
        int timeSteps = 10;
        var closingPrices = historicalPrices.stream().map(Bar::getClosePrice).map(Num::doubleValue)
            .toList();
        List<DataSet> trainingData = new ArrayList<>();
        for (int i = 0; i < historicalPrices.size() - timeSteps - 1; i++) {
            // Create input array with shape [1, 1, timeSteps]
            double[] priceWindow = closingPrices.subList(i, i + timeSteps).stream()
                .mapToDouble(Double::doubleValue)
                .toArray();
            // Reshape to [1, features(1), timeSteps]
            INDArray input = Nd4j.create(priceWindow, new int[]{1, 1, timeSteps});
            // Output shape should be [1, 1]
            // Create matching output of shape [1, 1, timeSteps]
            // We fill all positions with the same target value
            double[] outputValues = new double[timeSteps];
            double targetValue = closingPrices.get(i + timeSteps);
            Arrays.fill(outputValues, targetValue);
            INDArray output = Nd4j.create(outputValues, new int[]{1, 1, timeSteps});
            trainingData.add(new DataSet(input, output));
        }

        // Use a smaller batch size to avoid memory issues
        int batchSize = 32;
        DataSetIterator iterator = new ListDataSetIterator<>(trainingData, batchSize);

        MultiLayerNetwork model = buildModel(1, 1);

        // Add early stopping to avoid overfitting
        for (int epoch = 0; epoch < 100; epoch++) {
            iterator.reset();
            model.fit(iterator);
        }

        return model;
    }
}