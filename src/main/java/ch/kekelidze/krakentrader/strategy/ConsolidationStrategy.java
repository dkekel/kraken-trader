package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.RsiIndicator;
import ch.kekelidze.krakentrader.indicator.SimpleMovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.analyser.SupportResistanceAnalyser;
import ch.kekelidze.krakentrader.indicator.analyser.VolatilityAnalyzer;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Trade result 2017-2025: BacktestResult[totalProfit=560.0927084139926,
 * sharpeRatio=0.5137324757961464, maxDrawdown=96.25728406883614, winRate=0.25,
 * capital=40833.647510678915]
 * <p>
 * Implements a strategy for identifying market consolidation phases and making buy and sell
 * decisions based on multiple technical indicators such as support and resistance levels,
 * volatility, RSI, and MACD. The class integrates various analyzers and indicators to generate
 * trading signals within a given context.
 * <p>
 * This strategy determines buy or sell opportunities by analyzing: - Proximity of the current price
 * to identified support levels. - Reduction in market volatility. - Buy signals from Relative
 * Strength Index (RSI) and Moving Average Convergence Divergence (MACD) indicators.
 * <p>
 * The logic used ensures a systematic methodology to evaluate market conditions before making
 * trading decisions.
 */
@Slf4j
@Component("supportResistanceConsolidation")
@RequiredArgsConstructor
public class ConsolidationStrategy implements Strategy {

  private final SupportResistanceAnalyser supportResistanceAnalyser;
  private final VolatilityAnalyzer volatilityAnalyzer;
  private final RsiIndicator rsiIndicator;
  private final SimpleMovingAverageDivergenceIndicator simpleMovingAverageDivergenceIndicator;

  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var series = context.getBars();
    double currentPrice = series.getLast().getClosePrice().doubleValue();
    double atrThreshold = params.supportResistanceThreshold();

    var supportLevels = supportResistanceAnalyser.findSupportLevels(series,
        params.lookbackPeriod());
    boolean isNearSupport = supportResistanceAnalyser.isNearLevel(currentPrice, supportLevels,
        atrThreshold);

    boolean isVolatilityLow = volatilityAnalyzer.isVolatilityDecreasing(series,
        params.volatilityPeriod(), params.lookbackPeriod());

    var rsiSignal = rsiIndicator.isBuySignal(series, params);
    var macdSignal = simpleMovingAverageDivergenceIndicator.isBuySignal(series, params);

    log.debug("Near support: {}, isVolatilityLow: {}, rsiSignal: {}, macdSignal: {}",
        isNearSupport, isVolatilityLow, rsiSignal, macdSignal);
    return isNearSupport && isVolatilityLow && rsiSignal && macdSignal;
  }

  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var series = context.getBars();
    double currentPrice = series.getLast().getClosePrice().doubleValue();

    var resistanceLevels = supportResistanceAnalyser.findResistanceLevels(series,
        params.lookbackPeriod());
    boolean isNearResistance = supportResistanceAnalyser.isNearLevel(currentPrice, resistanceLevels,
        params.supportResistanceThreshold());

    boolean isVolatilityLow = volatilityAnalyzer.isVolatilityDecreasing(series,
        params.volatilityPeriod(), params.lookbackPeriod());

    var rsiSignal = rsiIndicator.isSellSignal(series, entryPrice, params);
    var macdSignal = simpleMovingAverageDivergenceIndicator.isSellSignal(series, entryPrice,
        params);

    log.debug("Near resistance: {}, isVolatilityLow: {}, rsiSignal: {}, macdSignal: {}",
        isNearResistance, isVolatilityLow, rsiSignal, macdSignal);
    return isNearResistance && isVolatilityLow && rsiSignal && macdSignal;
  }

  @Override
  public StrategyParameters getStrategyParameters() {
    return StrategyParameters.builder()
        .supportResistanceThreshold(1.1).supportResistancePeriod(20)
        .rsiBuyThreshold(35).rsiSellThreshold(70).rsiPeriod(14)
        .macdFastPeriod(12).macdSlowPeriod(26).macdSignalPeriod(9)
        .volatilityPeriod(14)
        .lookbackPeriod(10)
        .minimumCandles(150)
        .build();
  }
}
