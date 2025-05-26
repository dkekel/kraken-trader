package ch.kekelidze.krakentrader.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.kekelidze.krakentrader.indicator.MovingTrendIndicator;
import ch.kekelidze.krakentrader.indicator.RiskManagementIndicator;
import ch.kekelidze.krakentrader.indicator.SimpleMovingAverageDivergenceIndicator;
import ch.kekelidze.krakentrader.indicator.VolatilityIndicator;
import ch.kekelidze.krakentrader.indicator.VolumeIndicator;
import ch.kekelidze.krakentrader.indicator.analyser.TrendAnalyser;
import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.strategy.config.ConcurrentMapCacheConfig;
import ch.kekelidze.krakentrader.strategy.service.StrategyParametersService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(classes = {BuyLowSellHighStrategyTest.TestConfig.class,
    ConcurrentMapCacheConfig.class})
public class BuyLowSellHighStrategyTest {

    @TestConfiguration
    @EnableCaching
    static class TestConfig {
        @Bean
        public TrendAnalyser trendAnalyser() {
            return mock(TrendAnalyser.class);
        }

        @Bean
        public RiskManagementIndicator riskManagementIndicator() {
            return mock(RiskManagementIndicator.class);
        }

        @Bean
        public VolatilityIndicator volatilityIndicator() {
            return mock(VolatilityIndicator.class);
        }

        @Bean
        public SimpleMovingAverageDivergenceIndicator macdIndicator() {
            return mock(SimpleMovingAverageDivergenceIndicator.class);
        }

        @Bean
        public VolumeIndicator volumeIndicator() {
            return mock(VolumeIndicator.class);
        }

        @Bean
        public MovingTrendIndicator movingTrendIndicator() {
            return mock(MovingTrendIndicator.class);
        }

        @Bean
        public StrategyParametersService strategyParametersService() {
            return mock(StrategyParametersService.class);
        }

        @Bean
        public BuyLowSellHighStrategy buyLowSellHighStrategy(
                TrendAnalyser trendAnalyser,
                RiskManagementIndicator riskManagementIndicator,
                VolatilityIndicator volatilityIndicator,
                SimpleMovingAverageDivergenceIndicator macdIndicator,
                VolumeIndicator volumeIndicator,
                MovingTrendIndicator movingTrendIndicator,
                StrategyParametersService strategyParametersService) {
            return new BuyLowSellHighStrategy(
                trendAnalyser,
                riskManagementIndicator,
                volatilityIndicator,
                macdIndicator,
                volumeIndicator,
                movingTrendIndicator,
                strategyParametersService
            );
        }
    }

    @Autowired
    private StrategyParametersService strategyParametersService;

    @Autowired
    @Qualifier("buyLowSellHighStrategy")
    private Strategy strategy;

    @Test
    void getStrategyParameters_shouldCacheParameters() {
        // Arrange
        String coinPair = "BTC/USD";
        StrategyParameters expectedParameters = StrategyParameters.builder()
            .movingAverageBuyShortPeriod(15)
            .movingAverageBuyLongPeriod(53)
            .build();

        when(strategyParametersService.getStrategyParameters(coinPair))
            .thenReturn(Optional.of(expectedParameters));

        // Act - First call should hit the service
        StrategyParameters result1 = strategy.getStrategyParameters(coinPair);

        // Act - Second call should use the cache
        StrategyParameters result2 = strategy.getStrategyParameters(coinPair);

        // Assert
        assertEquals(expectedParameters, result1);
        assertSame(result1, result2); // Should be the same instance from cache
        verify(strategyParametersService, times(1)).getStrategyParameters(coinPair); // Service should be called only once
    }

    @Test
    void getStrategyParameters_withDifferentCoinPairs_shouldCallServiceForEach() {
        // Arrange
        String coinPair1 = "BTC/USD";
        String coinPair2 = "ETH/USD";
        StrategyParameters parameters1 = StrategyParameters.builder()
            .movingAverageBuyShortPeriod(15)
            .build();
        StrategyParameters parameters2 = StrategyParameters.builder()
            .movingAverageBuyShortPeriod(20)
            .build();

        when(strategyParametersService.getStrategyParameters(coinPair1))
            .thenReturn(Optional.of(parameters1));
        when(strategyParametersService.getStrategyParameters(coinPair2))
            .thenReturn(Optional.of(parameters2));

        // Act
        StrategyParameters result1 = strategy.getStrategyParameters(coinPair1);
        StrategyParameters result2 = strategy.getStrategyParameters(coinPair2);
        StrategyParameters result1Again = strategy.getStrategyParameters(coinPair1);
        StrategyParameters result2Again = strategy.getStrategyParameters(coinPair2);

        // Assert
        assertEquals(parameters1, result1);
        assertEquals(parameters2, result2);
        assertSame(result1, result1Again); // Should be the same instance from cache
        assertSame(result2, result2Again); // Should be the same instance from cache
        verify(strategyParametersService, times(1)).getStrategyParameters(coinPair1);
        verify(strategyParametersService, times(1)).getStrategyParameters(coinPair2);
    }
}
