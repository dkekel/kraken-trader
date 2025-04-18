package ch.kekelidze.krakentrader.backtester.service.dto;

import lombok.Builder;

@Builder
public record BacktestResult(double totalProfit, int totalTrades, double sharpeRatio,
                             double maxDrawdown, double winRate, double capital) {

}
