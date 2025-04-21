package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

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
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    double atr = atrAnalyser.calculateATR(data, params.atrPeriod());
    double currentPrice = data.getLast().getClosePrice().doubleValue();

    // Sell when volatility is high (above a higher threshold)
    return (atr / currentPrice) * 100 > params.highVolatilityThreshold();

  }
}
