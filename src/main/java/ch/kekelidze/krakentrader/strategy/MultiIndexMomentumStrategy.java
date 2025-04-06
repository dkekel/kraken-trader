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
        .build();
  }
}
