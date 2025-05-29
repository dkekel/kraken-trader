package ch.kekelidze.krakentrader.api.dto;

/**
 * Represents the result of a market order
 */
public record OrderResult(String orderId, double fee, double executedPrice, double volume) {

}
