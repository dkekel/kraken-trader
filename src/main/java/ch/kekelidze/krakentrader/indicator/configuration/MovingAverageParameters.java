package ch.kekelidze.krakentrader.indicator.configuration;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;

public interface MovingAverageParameters {

  int movingAverageBuyShortPeriod();

  int movingAverageBuyLongPeriod();

  int movingAverageSellShortPeriod();

  int movingAverageSellLongPeriod();
}
