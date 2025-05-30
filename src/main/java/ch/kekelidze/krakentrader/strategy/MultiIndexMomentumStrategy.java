package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MFIIndicator;
import ch.kekelidze.krakentrader.indicator.MovingAverageDivergenceCrossOverIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Trade result 2017-2025: BacktestResult[totalProfit=-414.57575941950097,
 * sharpeRatio=-0.5724765742956222, maxDrawdown=99.8382899728495, winRate=0.2875615763546798,
 * capital=43.46795260161401]
 * <p>
 * <b>15 minute candles</b>
 * <p>
 * <p>
 * MultiIndexMomentumStrategy is an implementation of the {@code Strategy} interface designed to
 * make buy and sell decisions in a financial market based on the combined analysis of multiple
 * technical indicators. This strategy uses Moving Average Convergence Divergence (MACD), Money Flow
 * Index (MFI), and Relative Strength Index (RSI) indicators to evaluate market conditions.
 * <p>
 * The strategy employs the following indicators: - MovingAverageDivergenceCrossOverIndicator:
 * Calculates MACD and its signal line to assess market momentum. - MFIIndicator: Evaluates the
 * Money Flow Index to determine overbought or oversold conditions. - RsiIndicator: Computes the
 * Relative Strength Index to measure the speed and change of price movements. -
 * RiskManagementIndicator: Used to make sell decisions based on risk and profit thresholds.
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
@Slf4j
@Component("multiIndexMomentum")
@RequiredArgsConstructor
public class MultiIndexMomentumStrategy implements Strategy {

  private static final int PERIOD = 15;

  private final MovingAverageDivergenceCrossOverIndicator movingAverageDivergenceCrossOverIndicator;
  private final MFIIndicator mfiIndicator;
  private final RsiIndicator rsiIndicator;
  private final RiskManagementIndicator riskManagementIndicator;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    return Stream.of(movingAverageDivergenceCrossOverIndicator, mfiIndicator, rsiIndicator).peek(
            indicator -> log.debug("Indicator: {}, Buy signal: {}",
                indicator.getClass().getSimpleName(), indicator.isBuySignal(context, params)))
        .allMatch(indicator -> indicator.isBuySignal(context, params));
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var riskManagementSignal = riskManagementIndicator.isSellSignal(context, entryPrice, params);
    log.debug("Risk management signal: {}", riskManagementSignal);
    return Stream.of(movingAverageDivergenceCrossOverIndicator, mfiIndicator, rsiIndicator).peek(
            indicator -> log.debug("Indicator: {}, Sell signal: {}",
                indicator.getClass().getSimpleName(),
                indicator.isSellSignal(context, entryPrice, params)))
        .allMatch(indicator -> indicator.isSellSignal(context, entryPrice, params))
        || riskManagementSignal;
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .rsiBuyThreshold(50).rsiSellThreshold(50).rsiPeriod(14)
        .macdFastPeriod(12).macdSlowPeriod(26).macdSignalPeriod(9)
        .mfiPeriod(20).mfiOversoldThreshold(40).mfiOverboughtThreshold(50)
        .lossPercent(5).profitPercent(5)
        .minimumCandles(26)
        .build();
  }

  @Override
  public int getPeriod() {
    return PERIOD;
  }
}
