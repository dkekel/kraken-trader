package ch.kekelidze.krakentrader.trade.util;

public class CoinNameUtils {

  public static String getValidCoinName(String coinPair) {
    if (coinPair != null && !coinPair.contains("/") && coinPair.endsWith("USD")) {
      return coinPair.substring(0, coinPair.length() - 3) + "/" + coinPair.substring(
          coinPair.length() - 3);
    }
    return coinPair;
  }
}
