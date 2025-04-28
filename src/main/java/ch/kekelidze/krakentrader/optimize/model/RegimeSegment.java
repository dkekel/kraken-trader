package ch.kekelidze.krakentrader.optimize.model;

/**
 * Data class to represent a segment of market regime
 */
public record RegimeSegment(int startIndex, int endIndex, RegimeType regimeType) {

  public int length() {
    return endIndex - startIndex + 1;
  }
}
