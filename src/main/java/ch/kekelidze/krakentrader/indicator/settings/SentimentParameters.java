package ch.kekelidze.krakentrader.indicator.settings;

public interface SentimentParameters {
  double sentimentBuyThreshold();
  double sentimentSellThreshold();
  boolean useSentimentForBuy();
  boolean useSentimentForSell();
  int sentimentLookbackPeriod();
  boolean useSentimentDivergence();
}
