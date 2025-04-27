package ch.kekelidze.krakentrader.trade;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TradeState {

  public TradeState(final String coinPair) {
    this.coinPair = coinPair;
  }

  final String coinPair;
  boolean inTrade;
  double entryPrice = 0;
  double positionSize = 0;
  double totalProfit = 0;

  public synchronized boolean isInTrade() {
    return inTrade;
  }

  public synchronized double getEntryPrice() {
    return entryPrice;
  }

  public synchronized double getPositionSize() {
    return positionSize;
  }

  public synchronized double getTotalProfit() {
    return totalProfit;
  }

  public synchronized String getCoinPair() {
    return coinPair;
  }

  public synchronized void setInTrade(boolean inTrade) {
    if (inTrade == this.inTrade) {
      log.warn("Tried to set trade state to the same value for {}", coinPair);
    }
    this.inTrade = inTrade;
  }

  public synchronized void setEntryPrice(double entryPrice) {
    this.entryPrice = entryPrice;
  }

  public synchronized void setPositionSize(double positionSize) {
    this.positionSize = positionSize;
  }

  public synchronized void setTotalProfit(double totalProfit) {
    this.totalProfit = totalProfit;
  }

  @Override
  public synchronized String toString() {
    return "TradeState{" +
        "coinPair='" + coinPair + '\'' +
        ", inTrade=" + inTrade +
        ", entryPrice=" + entryPrice +
        ", positionSize=" + positionSize +
        ", totalProfit=" + totalProfit +
        '}';
  }
}
