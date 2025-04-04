package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.AdxIndicator;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.indicator.MovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.PricePredictionIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class IndicatorAgreementStrategy implements Strategy {

  private final MovingAverageIndicator movingAverageIndicator;
  private final RsiIndicator rsiIndicator;
  private final PricePredictionIndicator pricePredictionIndicator;
  private final MovingAverageDivergenceIndicator movingAverageDivergenceIndicator;
  private final RiskManagementIndicator riskManagementIndicator;
  private final AdxIndicator adxIndicator;

  /**
   * MA Crossover (e.g., MA9 > MA21) AND
   * RSI < 30 (oversold) AND
   * ML Predicts Price Increase AND
   * Bullish Divergence (MACD/RSI).
   * @param data price data
   * @param params trade params
   * @return true if the asset should be bought according to the strategy, otherwise false
   */
  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    return adxIndicator.isBuySignal(data, params) &&
        Stream.of(movingAverageIndicator, rsiIndicator, pricePredictionIndicator,
            movingAverageDivergenceIndicator)
        .allMatch(indicator -> indicator.isBuySignal(data, params));
  }

  /**
   * MA Crossover (MA9 < MA21) OR
   * RSI > 70 (overbought) OR
   * ML Predicts Price Drop OR
   * Stop-Loss/Take-Profit Hit.
   * @param data price data
   * @param entryPrice bought price of the asset
   * @param params trade params
   * @return true if the asset should be sold according to the strategy, otherwise false
   */
  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    return adxIndicator.isSellSignal(data, entryPrice, params) &&
        Stream.of(movingAverageIndicator, rsiIndicator, pricePredictionIndicator,
            riskManagementIndicator)
        .anyMatch(indicator -> indicator.isSellSignal(data, entryPrice, params));
  }
}
