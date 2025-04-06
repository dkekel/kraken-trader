package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component("movingAverageScalper")
@RequiredArgsConstructor
public class MovingAverageScalper implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RiskManagementIndicator riskManagementIndicator;

  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    var buySignalParams = StrategyParameters.builder().movingAverageShortPeriod(9)
        .movingAverageLongPeriod(50).build();
    var buySignal = movingAverageIndicator.isBuySignal(data, buySignalParams);
    var ma50below100 = isMa50Below100(data);
    var ma100below200 = isMa100Below200(data);
    log.debug("Is buy signal: {}, MA50 below 100: {}, MA100 below 200: {}", buySignal, ma50below100,
        ma100below200);
    return buySignal && ma50below100 && ma100below200;
  }

  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    var sellSignalParams = StrategyParameters.builder().movingAverageShortPeriod(9)
        .movingAverageLongPeriod(200).build();
    var sellSignal = movingAverageIndicator.isSellSignal(data, entryPrice, sellSignalParams);
    var riskSellSignal = riskManagementIndicator.isSellSignal(data, entryPrice, params);
    var ma50greaterThan100 = isMa50GreaterThan100(data);
    var ma100greaterThan200 = isMa100GreaterThan200(data);
    log.debug(
        "Is sell signal: {}, Risk sell signal: {}, MA50 greater than 100: {}, MA100 greater than 200: {}",
        sellSignal, riskSellSignal, ma50greaterThan100, ma100greaterThan200);
    return sellSignal || riskSellSignal || ma50greaterThan100 || ma100greaterThan200;
  }

  private boolean isMa50Below100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isLessThan(ma100.getValue(endIndex));
  }

  private boolean isMa100Below200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isLessThan(ma200.getValue(endIndex));
  }

  private boolean isMa50GreaterThan100(List<Bar> data) {
    var ma50ma100Params = StrategyParameters.builder().movingAverageShortPeriod(50)
        .movingAverageLongPeriod(100).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma50ma100Params);
    var ma50 = movingAverage.maShort();
    var ma100 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma50.getValue(endIndex).isGreaterThan(ma100.getValue(endIndex));
  }

  private boolean isMa100GreaterThan200(List<Bar> data) {
    var ma100ma200Params = StrategyParameters.builder().movingAverageShortPeriod(100)
        .movingAverageLongPeriod(200).build();
    var movingAverage = movingAverageIndicator.calculateMovingAverage(data, ma100ma200Params);
    var ma100 = movingAverage.maShort();
    var ma200 = movingAverage.maLong();
    var endIndex = movingAverage.endIndex();
    return ma100.getValue(endIndex).isGreaterThan(ma200.getValue(endIndex));
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .movingAverageShortPeriod(9).movingAverageLongPeriod(50)
        .lossPercent(5).profitPercent(15)
        .minimumCandles(50)
        .build();
  }
}
