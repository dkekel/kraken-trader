package ch.kekelidze.krakentrader.trade;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TradeState {

  public TradeState(String coinPair) {
    this.coinPair = coinPair;
  }

  String coinPair;
  boolean inTrade;
  double entryPrice = 0;
  double positionSize = 0;
  double totalProfit = 0;

  public void reset() {
    this.coinPair = null;
    this.inTrade = false;
    this.entryPrice = 0;
    this.positionSize = 0;
    this.totalProfit = 0;
  }
}
