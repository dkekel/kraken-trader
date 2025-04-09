package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.PricePredictionIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component("pricePrediction")
@RequiredArgsConstructor
public class PricePredictionStrategy implements Strategy {

  private final PricePredictionIndicator pricePredictionIndicator;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    return pricePredictionIndicator.isBuySignal(data, params);
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    return pricePredictionIndicator.isSellSignal(data, entryPrice, params);
  }
}
