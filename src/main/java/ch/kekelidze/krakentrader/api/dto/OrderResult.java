package ch.kekelidze.krakentrader.api.dto;

/**
 * Represents the result of a market order
 */
public class OrderResult {

  private final String orderId;
  private final double fee;
  private final double executedPrice;
  private final double volume;

  public OrderResult(String orderId, double fee, double executedPrice, double volume) {
    this.orderId = orderId;
    this.fee = fee;
    this.executedPrice = executedPrice;
    this.volume = volume;
  }

  public String getOrderId() {
    return orderId;
  }

  public double getFee() {
    return fee;
  }

  public double getExecutedPrice() {
    return executedPrice;
  }

  public double getVolume() {
    return volume;
  }
}
