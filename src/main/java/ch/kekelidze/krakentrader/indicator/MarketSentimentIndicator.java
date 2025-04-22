package ch.kekelidze.krakentrader.indicator;

import ch.kekelidze.krakentrader.indicator.settings.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.service.SentimentDataService;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class MarketSentimentIndicator implements Indicator {

    private final SentimentDataService sentimentDataService;

    /**
     * Checks if the market sentiment is bullish for the given asset
     *
     * @param evaluationContext Context containing the asset symbol (e.g., "BTCUSD")
     * @param params Strategy parameters including sentiment thresholds
     * @return true if sentiment is bullish, false otherwise
     */
    @Override
    public boolean isBuySignal(EvaluationContext evaluationContext, StrategyParameters params) {
        var asset = evaluationContext.getSymbol();
        Bar currentBar = evaluationContext.getBars().getLast();
        long timestamp = currentBar.getEndTime().toEpochSecond();
        double sentimentScore = sentimentDataService.getSentimentScore(asset, timestamp);
        log.debug("Sentiment for {} at {}: {}", asset, timestamp, sentimentScore);

        return sentimentScore > params.sentimentBuyThreshold();
    }

    @Override
    public boolean isSellSignal(EvaluationContext context, double entryPrice,
        StrategyParameters params) {
        var asset = context.getSymbol();
        Bar currentBar = context.getBars().getLast();
        long timestamp = currentBar.getEndTime().toEpochSecond();
        double sentimentScore = sentimentDataService.getSentimentScore(asset, timestamp);
        return sentimentScore < params.sentimentSellThreshold();
    }
    
    /**
     * Calculates the sentiment change over a period
     * 
     * @param asset The asset symbol
     * @param bars The price bars
     * @param lookbackBars Number of bars to look back
     * @return Rate of change in sentiment (-100 to +100)
     */
    public double calculateSentimentChange(String asset, List<Bar> bars, int lookbackBars) {
        if (bars.size() < lookbackBars) {
            return 0.0;
        }
        
        Bar currentBar = bars.getLast();
        Bar pastBar = bars.get(bars.size() - lookbackBars);
        
        long currentTime = currentBar.getEndTime().toEpochSecond();
        long pastTime = pastBar.getEndTime().toEpochSecond();
        
        double currentSentiment = sentimentDataService.getSentimentScore(asset, currentTime);
        double pastSentiment = sentimentDataService.getSentimentScore(asset, pastTime);
        
        return currentSentiment - pastSentiment;
    }
    
    /**
     * Detects a sentiment divergence with price
     * (Price moving up while sentiment declining, or vice versa)
     * 
     * @param asset The asset symbol
     * @param bars The price bars
     * @param lookbackBars Number of bars to look back
     * @return true if there's a bearish divergence, false otherwise
     */
    public boolean isBearishSentimentDivergence(String asset, List<Bar> bars, int lookbackBars) {
        if (bars.size() < lookbackBars) {
            return false;
        }
        
        Bar currentBar = bars.getLast();
        Bar pastBar = bars.get(bars.size() - lookbackBars);
        
        // Price trend
        boolean priceUp = currentBar.getClosePrice().doubleValue() > 
                          pastBar.getClosePrice().doubleValue();
        
        // Sentiment trend
        long currentTime = currentBar.getEndTime().toEpochSecond();
        long pastTime = pastBar.getEndTime().toEpochSecond();
        
        double currentSentiment = sentimentDataService.getSentimentScore(asset, currentTime);
        double pastSentiment = sentimentDataService.getSentimentScore(asset, pastTime);
        
        boolean sentimentDown = currentSentiment < pastSentiment;
        
        // Bearish divergence: price up, sentiment down
        return priceUp && sentimentDown;
    }
    
    /**
     * Detects a bullish sentiment divergence with price
     * (Price moving down while sentiment improving)
     * 
     * @param asset The asset symbol
     * @param bars The price bars
     * @param lookbackBars Number of bars to look back
     * @return true if there's a bullish divergence, false otherwise
     */
    public boolean isBullishSentimentDivergence(String asset, List<Bar> bars, int lookbackBars) {
        if (bars.size() < lookbackBars) {
            return false;
        }
        
        Bar currentBar = bars.getLast();
        Bar pastBar = bars.get(bars.size() - lookbackBars);
        
        // Price trend
        boolean priceDown = currentBar.getClosePrice().doubleValue() < 
                            pastBar.getClosePrice().doubleValue();
        
        // Sentiment trend
        long currentTime = currentBar.getEndTime().toEpochSecond();
        long pastTime = pastBar.getEndTime().toEpochSecond();
        
        double currentSentiment = sentimentDataService.getSentimentScore(asset, currentTime);
        double pastSentiment = sentimentDataService.getSentimentScore(asset, pastTime);
        
        boolean sentimentUp = currentSentiment > pastSentiment;
        
        // Bullish divergence: price down, sentiment up
        return priceDown && sentimentUp;
    }
}