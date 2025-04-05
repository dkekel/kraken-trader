package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.PricePredictionIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
@RequiredArgsConstructor
public class PricePredictionStrategy implements Strategy {

  private final PricePredictionIndicator pricePredictionIndicator;

  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    return pricePredictionIndicator.isBuySignal(data, params);
  }

  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    return pricePredictionIndicator.isSellSignal(data, entryPrice, params);
  }
}
