package ch.kekelidze.krakentrader.indicator.configuration;

public interface VolumeParameters {
    int volumePeriod();
    double aboveAverageThreshold();
    double volumeSurgeBearishThreshold();
    double volumeSurgeExtremeBearishThreshold();
}