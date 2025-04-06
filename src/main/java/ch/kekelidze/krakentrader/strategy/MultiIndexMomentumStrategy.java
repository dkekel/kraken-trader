package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MFIIndicator;
import ch.kekelidze.krakentrader.indicator.MovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

/**
 * <b>15 minute candles</b>
 * <p>
 * <p>
 * MultiIndexMomentumStrategy is an implementation of the {@code Strategy} interface designed to
 * make buy and sell decisions in a financial market based on the combined analysis of multiple
 * technical indicators. This strategy uses Moving Average Convergence Divergence (MACD), Money Flow
 * Index (MFI), and Relative Strength Index (RSI) indicators to evaluate market conditions.
 * <p>
 * The strategy employs the following indicators: - MovingAverageDivergenceIndicator: Calculates
 * MACD and its signal line to assess market momentum. - MFIIndicator: Evaluates the Money Flow
 * Index to determine overbought or oversold conditions. - RsiIndicator: Computes the Relative
 * Strength Index to measure the speed and change of price movements. - RiskManagementIndicator:
 * Used to make sell decisions based on risk and profit thresholds.
 * <p>
 * The strategy makes a buy decision when all individual indicators signal a buy condition.
 * Similarly, it makes a sell decision if all indicators signal a sell condition, or if the
 * RiskManagementIndicator suggests selling due to conditions like loss or profit thresholds.
 * <p>
 * The default strategy parameters, such as thresholds and periods for each indicator, are defined
 * using the {@code StrategyParameters} builder within the implementation.
 * <p>
 * This class is designed to be used as a component in a Spring application and is dependent on the
 * specific implementations of the indicators mentioned above.
 */
@Component("multiIndexMomentum")
@RequiredArgsConstructor
public class MultiIndexMomentumStrategy implements Strategy {

  private final MovingAverageDivergenceIndicator movingAverageDivergenceIndicator;
  private final MFIIndicator mfiIndicator;
  private final RsiIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;

  @Override
  public boolean shouldBuy(List<Bar> data, StrategyParameters params) {
    return Stream.of(movingAverageDivergenceIndicator, mfiIndicator, rsiIndicator)
        .allMatch(indicator -> indicator.isBuySignal(data, params));
  }

  @Override
  public boolean shouldSell(List<Bar> data, double entryPrice, StrategyParameters params) {
    return Stream.of(movingAverageDivergenceIndicator, mfiIndicator, rsiIndicator)
        .allMatch(indicator -> indicator.isSellSignal(data, entryPrice, params))
        || riskManagementIndicator.isSellSignal(data, entryPrice, params);
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .rsiBuyThreshold(50).rsiSellThreshold(50).rsiPeriod(14)
        .macdShortBarCount(12).macdLongBarCount(26).macdBarCount(9)
        .mfiPeriod(20).mfiOversoldThreshold(40).mfiOverboughtThreshold(50)
        .lossPercent(5).profitPercent(5)
        .minimumCandles(26)
        .build();
  }
}
