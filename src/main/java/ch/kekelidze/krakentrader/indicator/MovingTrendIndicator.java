package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.analyser.BollingerContractionAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Slf4j
@Component
@RequiredArgsConstructor
public class MovingTrendIndicator implements Indicator {

  private final BollingerContractionAnalyser bollingerContractionAnalyser;

  @Override
  public boolean isBuySignal(EvaluationContext context, StrategyParameters params) {
    var data = context.getBars();
    return isMovingMarketTrend(data, params);
  }

  @Override
  public boolean isSellSignal(EvaluationContext context, double entryPrice,
      StrategyParameters params) {
    var data = context.getBars();
    return isMovingMarketTrend(data, params);
  }

  /**
   * Master method that integrates multiple sideways market detection techniques
   *
   * @param data List of price bars
   * @return true if the market appears to be in a sideways/consolidation phase
   */
  private boolean isMovingMarketTrend(List<Bar> data, StrategyParameters parameters) {
    boolean sidewaysChannel = isInSidewaysChannel(data, parameters.movingAverageBuyShortPeriod(),
        parameters.contractionThreshold());

    boolean bollingerContraction = bollingerContractionAnalyser.hasBollingerContraction(data,
        parameters.movingAverageBuyShortPeriod(), parameters.contractionThreshold());

    var movingTrend = !(sidewaysChannel && bollingerContraction);
    log.debug(
        "Market moving result: {}, calculated using sidewaysChannel={} and bollingerContraction={}",
        movingTrend, sidewaysChannel, bollingerContraction);

    return movingTrend;
  }

  /**
   * Detects if the market is in a sideways channel by analyzing price range.
   *
   * @param data List of price bars
   * @param lookbackPeriod Period to analyze
   * @param channelThreshold Maximum percentage range to consider as sideways
   * @return true if the market is in a sideways channel
   */
  private boolean isInSidewaysChannel(List<Bar> data, int lookbackPeriod, double channelThreshold) {
    if (data.size() < lookbackPeriod) {
      return false;
    }

    // Get the recent price data
    List<Bar> recentBars = data.subList(data.size() - lookbackPeriod, data.size());

    double highestHigh = Double.MIN_VALUE;
    double lowestLow = Double.MAX_VALUE;

    for (Bar bar : recentBars) {
      if (bar.getHighPrice().doubleValue() > highestHigh) {
        highestHigh = bar.getHighPrice().doubleValue();
      }
      if (bar.getLowPrice().doubleValue() < lowestLow) {
        lowestLow = bar.getLowPrice().doubleValue();
      }
    }

    // Calculate the range as a percentage of the average price
    double avgPrice = (highestHigh + lowestLow) / 2;
    double percentRange = ((highestHigh - lowestLow) / avgPrice) * 100;

    // If the range is less than the threshold, consider it sideways
    log.debug("Sideways channel: {} < {}", percentRange, channelThreshold);
    return percentRange < channelThreshold;
  }
}
