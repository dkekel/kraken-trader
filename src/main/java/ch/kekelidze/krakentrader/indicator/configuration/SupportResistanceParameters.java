package ch.kekelidze.krakentrader.indicator.configuration;

public interface SupportResistanceParameters {
    int supportResistancePeriod();
    double supportResistanceThreshold();
    int minimumCandles();
}