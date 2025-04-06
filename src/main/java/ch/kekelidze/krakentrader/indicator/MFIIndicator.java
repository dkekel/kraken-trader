package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
public class MFIIndicator implements Indicator {

  @Override
  public boolean isBuySignal(List<Bar> data, StrategyParameters params) {
    double mfi = calculateMFI(data, params.mfiPeriod());
    log.debug("MFI: {}, Buy threshold: {}, Closing time: {}", mfi, params.mfiOversoldThreshold(),
        data.getLast().getEndTime());
    return mfi < params.mfiOversoldThreshold();
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double mfi = calculateMFI(data, params.mfiPeriod());
    log.debug("MFI: {}, Sell threshold: {}, Closing time: {}", mfi, params.mfiOverboughtThreshold(),
        data.getLast().getEndTime());
    return mfi > params.mfiOverboughtThreshold();
  }

  private double calculateMFI(List<Bar> data, int period) {
    if (data.size() < period + 1) {
      throw new IllegalArgumentException("Insufficient data to calculate MFI.");
    }

    double positiveFlowSum = 0;
    double negativeFlowSum = 0;

    for (int i = 1; i <= period; i++) {
      Bar current = data.get(data.size() - i);
      Bar previous = data.get(data.size() - i - 1);

      double typicalPriceCurrent =
          (current.getHighPrice().doubleValue() + current.getLowPrice().doubleValue()
              + current.getClosePrice().doubleValue()) / 3;
      double typicalPricePrevious =
          (previous.getHighPrice().doubleValue() + previous.getLowPrice().doubleValue()
              + previous.getClosePrice().doubleValue()) / 3;

      double rawMoneyFlow = typicalPriceCurrent * current.getVolume().doubleValue();
      if (typicalPriceCurrent > typicalPricePrevious) {
        positiveFlowSum += rawMoneyFlow;
      } else if (typicalPriceCurrent < typicalPricePrevious) {
        negativeFlowSum += rawMoneyFlow;
      }
    }

    if (negativeFlowSum == 0) {
      return 100.0; // MFI at overbought limit
    }

    double moneyFlowRatio = positiveFlowSum / negativeFlowSum;
    return 100 - (100 / (1 + moneyFlowRatio));
  }

}
