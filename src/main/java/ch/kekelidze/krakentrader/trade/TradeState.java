package ch.kekelidze.krakentrader.trade;

import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class TradeState {

  double initialCapital = 10000;
  boolean inTrade;
  double entryPrice = 0;
  double totalProfit = 0;

  public void reset() {
    this.initialCapital = 10000;
    this.inTrade = false;
    this.entryPrice = 0;
    this.totalProfit = 0;
  }
}
