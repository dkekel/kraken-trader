package ch.kekelidze.krakentrader.trade.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskManagementService {

  private static final double CAPITAL = 1000; // $1000
  private static final double RISK_PERCENT = 1; // 1%

  public boolean shouldStopLoss(double entryPrice, double currentPrice, double lossPercent) {
    return currentPrice <= entryPrice * (1 - lossPercent / 100);
  }

  public boolean shouldTakeProfit(double entryPrice, double currentPrice,
      double profitPercent) {
    return currentPrice >= entryPrice * (1 + profitPercent / 100);
  }

  public double getPositionSize(double entryPrice) {
    double stopLossDistance = entryPrice * 0.05; // 5% SL
    double positionSize = (CAPITAL * RISK_PERCENT / 100) / stopLossDistance;
    log.info("Position size: {}", positionSize);
    return positionSize;
  }
}
