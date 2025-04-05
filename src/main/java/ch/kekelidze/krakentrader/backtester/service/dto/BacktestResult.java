package ch.kekelidze.krakentrader.backtester.service.dto;

import lombok.Builder;

@Builder
public record BacktestResult(double totalProfit, double sharpeRatio, double maxDrawdown,
                             double winRate, double capital) {

}
