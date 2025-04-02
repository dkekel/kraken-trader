package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.indicator.service.IndicatorService;
import ch.kekelidze.krakentrader.indicator.service.strategy.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;
import org.ta4j.core.num.Num;

@Slf4j
@Service
@RequiredArgsConstructor
public class TradeStrategyService {

  private final IndicatorService indicatorService;
  private final RiskManagementService riskManagementService;

  public void executeStrategy(List<Bar> data, MultiLayerNetwork model, StrategyParameters params) {
    boolean inTrade = false;
    double entryPrice = 0;
    double totalProfit = 0;

    // Start after long MA is available
    for (int i = params.movingAverageLongPeriod(); i < data.size(); i++) {
      List<Bar> sublist = data.subList(i - params.movingAverageLongPeriod(), i);
      double maShort = indicatorService.calculateMovingAverage(sublist,
          params.movingAverageShortPeriod());
      double maLong = indicatorService.calculateMovingAverage(sublist,
          params.movingAverageLongPeriod());
      double rsi = indicatorService.calculateRSI(sublist, params.rsiPeriod());
      double macd = indicatorService.calculateMovingAverageDivergence(sublist);

      // Prepare input sequence (last 10 prices)
      var inputSequence = sublist.subList(sublist.size() - 10, sublist.size()).stream()
          .map(Bar::getClosePrice).map(Num::doubleValue).toArray(Double[]::new);
      double[] inputArray = new double[inputSequence.length];
      for (int j = 0; j < inputSequence.length; j++) {
        inputArray[j] = inputSequence[j];
      }

      // Reshape to [1, 1, timeSteps] for LSTM input
      INDArray input = Nd4j.create(inputArray, new int[]{1, 1, inputArray.length});
      INDArray output = model.output(input);
      double predictedPrice = output.getDouble(0);

      var currentPrice = data.get(i).getClosePrice().doubleValue();
      var previousPrice = data.get(i - 1).getClosePrice().doubleValue();
      var macdSignal = indicatorService.calculateMacdSignal(sublist);

      if (!inTrade && (predictedPrice > previousPrice
          || maCrossesAbove(maShort, maLong) && macd > macdSignal && rsi < params.rsiBuyThreshold())) {
        inTrade = true;
        entryPrice = currentPrice;
        log.info("BUY at: {}", entryPrice);
      } else if (inTrade
          && (maCrossesAbove(maLong, maShort) || macd < macdSignal || rsi > params.rsiSellThreshold()
          || predictedPrice < previousPrice
          || riskManagementService.shouldStopLoss(entryPrice, currentPrice, 5)
          || riskManagementService.shouldTakeProfit(entryPrice, currentPrice, 10))) {
        inTrade = false;
        double profit = (currentPrice - entryPrice) / entryPrice * 100;
        totalProfit += profit;
        log.info("SELL at: {} | Profit: {}%", currentPrice, profit);
      }
    }
    log.info("Total Profit: {}%", totalProfit);
  }

  /**
   * Helper method to check if a short moving average crosses above a long moving average.
   */
  private boolean maCrossesAbove(double shortMa, double longMa) {
    return shortMa > longMa;
  }
}
