package ch.kekelidze.krakentrader.strategy;

import ch.kekelidze.krakentrader.indicator.MovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.MovingAverageIndicator;
import ch.kekelidze.krakentrader.indicator.PricePredictionIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.optimize.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("weightedAgreement")
@RequiredArgsConstructor
public class WeightedAgreementStrategy implements Strategy {

  private final PricePredictionIndicator pricePredictionIndicator;
  private final MovingAverageIndicator movingAverageIndicator;
  private final MovingAverageDivergenceIndicator movingAverageDivergenceIndicator;
  private final VolumeIndicator volumeIndicator;

  /**
   * Assign weights to indicators based on backtested reliability:
   * <p>
   * * ML Prediction	35%	Predictive power * MA Crossover	30%	Trend alignment * RSI +
   * Divergence	25%	Momentum + reversal * Volume	10%	Confirmation Buy
   *
   * @param context context with the coin symbol and a list of price bars, representing the market
   *                data to analyze
   * @param params  trade params
   * @return true if total score > {@link StrategyParameters#weightedAgreementThreshold()}
   */
  @Override
  public boolean shouldBuy(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    var mlScore = pricePredictionIndicator.isBuySignal(data, params) ? 1 : 0;
    var maScore = movingAverageIndicator.isBuySignal(data, params) ? 1 : 0;
    var divergenceScore = movingAverageDivergenceIndicator.isBuySignal(data, params) ? 1 : 0;
    var volumeScore = volumeIndicator.isBuySignal(data, params) ? 1 : 0;
    double score = calculateTotalScore(mlScore, maScore, divergenceScore, volumeScore);
    log.debug("Buy score: {}, Agreement threshold: {}", score, params.weightedAgreementThreshold());
    return score >= params.weightedAgreementThreshold() / 100;
  }

  /**
   * Assign weights to indicators based on backtested reliability:
   * <p>
   * * ML Prediction	35%	Predictive power * MA Crossover	30%	Trend alignment * RSI +
   * Divergence	25%	Momentum + reversal * Volume	10%	Confirmation Sell
   *
   * @param context context with the coin symbol and a list of price bars, representing the market
   *                data to analyze
   * @param params  trade params
   * @return true if total score > {@link StrategyParameters#weightedAgreementThreshold()}
   */
  @Override
  public boolean shouldSell(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    var mlScore = pricePredictionIndicator.isSellSignal(data, entryPrice, params) ? 1 : 0;
    var maScore = movingAverageIndicator.isSellSignal(data, entryPrice, params) ? 1 : 0;
    var divergenceScore =
        movingAverageDivergenceIndicator.isSellSignal(data, entryPrice, params) ? 1 : 0;
    var volumeScore = volumeIndicator.isSellSignal(data, entryPrice, params) ? 1 : 0;
    double score = calculateTotalScore(mlScore, maScore, divergenceScore, volumeScore);
    log.debug("Sell score: {}, Agreement threshold: {}", score,
        params.weightedAgreementThreshold());
    return score >= params.weightedAgreementThreshold() / 100;
  }

  private double calculateTotalScore(double mlScore, double maScore, double divergenceScore,
      double volumeScore) {
    return (mlScore * 0.35) + (maScore * 0.30) + (divergenceScore * 0.25) + (volumeScore * 0.10);
  }
}
