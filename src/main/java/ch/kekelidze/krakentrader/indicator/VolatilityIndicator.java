package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.settings.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VolatilityIndicator implements Indicator {

  private final AtrAnalyser atrAnalyser;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();
    return (atr / currentPrice) * 100 < params.lowVolatilityThreshold();
  }

  @Override
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();

    // Sell when volatility is high (above a higher threshold)
    return (atr / currentPrice) * 100 > params.highVolatilityThreshold();

  }
}
