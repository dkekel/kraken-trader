package ch.kekelidze.krakentrader.indicator;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.indicator.analyser.AtrAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;

public class RiskManagementIndicatorTest {

  private AtrAnalyser atrAnalyser;
  private TradingApiService tradingApiService;
  private RiskManagementIndicator indicator;

  private static final String SYMBOL = "BTC/USD";
  private static final double ENTRY_PRICE = 100.0;

  @BeforeEach
  void setUp() {
    atrAnalyser = mock(AtrAnalyser.class);
    tradingApiService = mock(TradingApiService.class);
    indicator = new RiskManagementIndicator(atrAnalyser, tradingApiService);

    // Simplify dynamic price: currentPrice = lastClose - (highVolatilityThreshold * ATR)
    // We'll set ATR = 0 in tests -> currentPrice = lastClose
    when(atrAnalyser.calculateATR(anyList(), anyInt())).thenReturn(0.0);

    // Set taker fee so that breakeven = entry * (1 + 2*fee/100)
    // Using 0.25% taker -> roundTrip 0.5% -> breakeven = 100.5 for ENTRY_PRICE=100
    when(tradingApiService.getCoinTradingFee(SYMBOL)).thenReturn(0.25);
  }

  private static Bar bar(double close) {
    ZonedDateTime now = ZonedDateTime.now();
    return new BaseBar(Duration.ofMinutes(1), now, close, close, close, close, 1d);
  }

  private static EvaluationContext ctx(String symbol, List<Bar> bars) {
    return EvaluationContext.builder()
        .symbol(symbol)
        .bars(bars)
        .build();
  }

  private static StrategyParameters params(double lossPercent, double profitPercent,
      double highVolatilityThreshold, int atrPeriod) {
    return StrategyParameters.builder()
        .lossPercent(lossPercent)
        .profitPercent(profitPercent)
        .highVolatilityThreshold(highVolatilityThreshold)
        .atrPeriod(atrPeriod)
        .build();
  }

  @Test
  void trailingSell_triggersAfterTwoConsecutiveDeclinesAboveThreshold_andAboveBreakeven() {
    // Given
    // threshold = max(0.5, highVolatilityThreshold * 0.5). Set highVolatilityThreshold = 0 -> threshold = 0.5
    StrategyParameters p = params(50.0, 1000.0, 0.0, 14);
    double breakeven = ENTRY_PRICE * (1 + (0.25 * 2) / 100); // 100.5

    // Sequence: Rise to 110 (activate trailing), then two declines 109 and 107
    // Decline from peak 110 to 107 = ~2.727% >= 0.5% and 2 consecutive declines -> should sell

    // 1) Rising to peak: 110
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(110))), ENTRY_PRICE, p));

    // 2) First decline: 109 (above breakeven)
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(109))), ENTRY_PRICE, p));
    assertTrue(109.0 > breakeven);

    // 3) Second decline: 107 (above breakeven) -> should trigger
    assertTrue(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(107))), ENTRY_PRICE, p));
    assertTrue(107.0 > breakeven);
  }

  @Test
  void trailingSell_doesNotTriggerOnSingleDecline() {
    StrategyParameters p = params(50.0, 1000.0, 0.0, 14);

    // Peak 105, then a single decline to 104.7 (0.2857% decline) < 0.5% and also only 1 decline -> no sell
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(105))), ENTRY_PRICE, p));
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(104.7))), ENTRY_PRICE, p));
  }

  @Test
  void trailingSell_stateResetsWhenBelowBreakeven_andRequiresNewSequence() {
    StrategyParameters p = params(50.0, 1000.0, 0.0, 14);

    double breakeven = ENTRY_PRICE * (1 + (0.25 * 2) / 100); // 100.5

    // 1) Go above breakeven and establish a peak
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(103))), ENTRY_PRICE, p));
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(104))), ENTRY_PRICE, p));

    // 2) Drop below breakeven -> state should reset
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(100.4))), ENTRY_PRICE, p));
    assertTrue(100.4 < breakeven);

    // 3) Go above breakeven again: build a new peak sequence
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(106))), ENTRY_PRICE, p)); // new peak
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(105.8))), ENTRY_PRICE,
        p)); // first small decline (<0.5%)

    // Second small decline from peak, still below threshold -> should NOT trigger
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(105.6))), ENTRY_PRICE, p));

    // Now make a new higher peak to reset decline count
    assertFalse(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(107))), ENTRY_PRICE,
        p)); // new higher peak resets declines
    // Two proper declines after reset to trigger
    assertFalse(
        indicator.isSellSignal(ctx(SYMBOL, List.of(bar(106))), ENTRY_PRICE, p)); // first decline
    assertTrue(indicator.isSellSignal(ctx(SYMBOL, List.of(bar(104.5))), ENTRY_PRICE,
        p)); // second decline > threshold
  }
}
