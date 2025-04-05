package ch.kekelidze.krakentrader.api.dto;

import java.time.ZonedDateTime;
import lombok.Builder;

@Builder
public record OhlcEntry(
    String symbol,
    double open,
    double high,
    double low,
    double close,
    int trades,
    double volume,
    double vwap,
    ZonedDateTime intervalBegin,
    int interval,
    ZonedDateTime timestamp
) {

}
