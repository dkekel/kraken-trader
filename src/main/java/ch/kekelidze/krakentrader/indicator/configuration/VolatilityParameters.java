package ch.kekelidze.krakentrader.indicator.configuration;

public interface VolatilityParameters {
    int volatilityPeriod();
    double lowVolatilityThreshold();
    double highVolatilityThreshold();
    int lookbackPeriod();
}