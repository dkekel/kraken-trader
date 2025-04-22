package ch.kekelidze.krakentrader.indicator.settings;

public interface VolatilityParameters {
    int volatilityPeriod();
    double lowVolatilityThreshold();
    double highVolatilityThreshold();
    int lookbackPeriod();
}