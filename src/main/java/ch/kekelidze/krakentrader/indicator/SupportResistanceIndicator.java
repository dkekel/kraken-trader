package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.analyser.SupportResistanceAnalyser;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class SupportResistanceIndicator implements Indicator {

  private final SupportResistanceAnalyser supportResistanceAnalyser;

  /**
   * Determines whether the current conditions meet the criteria for a buy signal based on the
   * proximity of the price to defined support levels.
   *
   * @param context context with the list of price bars representing historical data
   * @param params the strategy parameters used to adjust the buy signal calculation
   * @return true if the current market conditions indicate a buy signal, false otherwise
   */
  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var supportLevels = supportResistanceAnalyser.findSupportLevels(data,
        params.supportResistancePeriod());
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    return supportLevels.stream()
        .anyMatch(level -> Math.abs(currentPrice - level)
            <= currentPrice * params.supportResistancePeriod());
  }

  /**
   * Determines whether the current market conditions indicate a sell signal based on the
   * proximity of the price to resistance levels.
   *
   * @param data the list of price bars representing historical market data
   * @param entryPrice the price at which the asset was initially purchased or entered
   * @param params the strategy parameters containing configuration values used in the
   *               sell signal calculation
   * @return true if the current market conditions meet the criteria for a sell signal,
   *         false otherwise
   */
  @Override
  public boolean isSellSignal(List<Bar> data, double entryPrice, StrategyParameters params) {
    var resistanceLevels = supportResistanceAnalyser.findResistanceLevels(data,
        params.supportResistancePeriod());
    var currentPrice = data.getLast().getClosePrice().doubleValue();
    return resistanceLevels.stream()
        .anyMatch(level -> Math.abs(currentPrice - level)
            <= currentPrice * params.supportResistancePeriod());
  }
}
