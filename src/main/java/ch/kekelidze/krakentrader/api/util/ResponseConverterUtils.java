package ch.kekelidze.krakentrader.api.util;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.json.JSONArray;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BaseBar;
import org.ta4j.core.num.DecimalNum;

@Component
public class ResponseConverterUtils {
  public Bar getPriceBar(JSONArray ohlcCandle, int period) {
    return BaseBar.builder(DecimalNum::valueOf, Number.class)
        .timePeriod(Duration.ofMinutes(period))
        .endTime(ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(ohlcCandle.getLong(0)),
            ZoneId.systemDefault()))
        .openPrice(DecimalNum.valueOf(ohlcCandle.getDouble(1)))
        .highPrice(DecimalNum.valueOf(ohlcCandle.getDouble(2)))
        .lowPrice(DecimalNum.valueOf(ohlcCandle.getDouble(3)))
        .closePrice(DecimalNum.valueOf(ohlcCandle.getDouble(4)))
        .volume(DecimalNum.valueOf(ohlcCandle.getDouble(6)))
        .build();
  }
}
