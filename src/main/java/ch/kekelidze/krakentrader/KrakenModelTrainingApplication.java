package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.optimize.model.LSTMModel;
import ch.kekelidze.krakentrader.api.file.service.CsvFileService;
import ch.kekelidze.krakentrader.optimize.util.TestDataUtils;
import java.io.File;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {CsvFileService.class, LSTMModel.class, TestDataUtils.class}
)
public class KrakenModelTrainingApplication {

  public static void main(String[] args) throws IOException {
    String coin = args[0];
    int trainingInterval = Integer.parseInt(args[1]);
    var application = SpringApplication.run(KrakenModelTrainingApplication.class, args);
    optimizeMLModel(application, coin, trainingInterval);
  }

  private static void optimizeMLModel(ApplicationContext application, String coin,
      int trainingInterval) throws IOException {
    var fileName = coin + "_" + trainingInterval;
    var krakenCsvService = application.getBean(CsvFileService.class);
    var lstmModel = application.getBean(LSTMModel.class);
    var testDataService = application.getBean(TestDataUtils.class);
    var historicalData = krakenCsvService.readCsvFile("data/" + fileName + ".csv");
    var modelFile = new File(fileName + "_model.h5");
    if (!modelFile.exists()) {
      var trainingData = testDataService.getTestData(historicalData);
      var model = lstmModel.trainModel(trainingData);
      model.save(modelFile);
    }
  }
}
