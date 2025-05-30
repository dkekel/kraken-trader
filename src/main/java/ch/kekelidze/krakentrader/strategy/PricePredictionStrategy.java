package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.PredictionModelExistCondition;
import ch.kekelidze.krakentrader.indicator.PricePredictionIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Component("pricePrediction")
@RequiredArgsConstructor
@Conditional(PredictionModelExistCondition.class)
public class PricePredictionStrategy implements Strategy {

  private final PricePredictionIndicator pricePredictionIndicator;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    return pricePredictionIndicator.isBuySignal(context, params);
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    return pricePredictionIndicator.isSellSignal(context, entryPrice, params);
  }
}
