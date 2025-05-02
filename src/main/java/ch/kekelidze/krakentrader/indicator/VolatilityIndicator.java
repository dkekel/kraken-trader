package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class VolatilityIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    double volatilityPercentage = calculateVolatilityPercentage(data, params);
    return volatilityPercentage < params.lowVolatilityThreshold();
  }

  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double volatilityPercentage = calculateVolatilityPercentage(data, params);
    return volatilityPercentage > params.highVolatilityThreshold();
  }

  private double calculateVolatilityPercentage(List<Bar> data, StrategyParameters parameters) {
    double atr = atrAnalyser.calculateATR(data, parameters.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    double volatilityPercentage = (atr / currentPrice) * 100;
    log.debug(
        "Volatility calculation - ATR: {}, Current price: {}, Volatility: {}%, Threshold: {}%",
        atr, currentPrice, volatilityPercentage, parameters.lowVolatilityThreshold());
    return volatilityPercentage;
  }
}
