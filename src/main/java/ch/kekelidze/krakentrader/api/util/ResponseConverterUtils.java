package ch.kekelidze.krakentrader.api.util;

import ch.kekelidze.krakentrader.api.dto.OhlcEntry;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.json.JSONArray;
import org.json.JSONObject;
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

  public Bar getPriceBarFromOhlcEntry(OhlcEntry ohlcCandle) {
    return BaseBar.builder(DecimalNum::valueOf, Number.class)
        .timePeriod(Duration.ofMinutes(ohlcCandle.interval()))
        .endTime(ohlcCandle.timestamp())
        .openPrice(DecimalNum.valueOf(ohlcCandle.open()))
        .highPrice(DecimalNum.valueOf(ohlcCandle.high()))
        .lowPrice(DecimalNum.valueOf(ohlcCandle.low()))
        .closePrice(DecimalNum.valueOf(ohlcCandle.close()))
        .volume(DecimalNum.valueOf(ohlcCandle.volume()))
        .build();
  }

  // Method to convert a JsonObject to an OhlcEntry
  public OhlcEntry convertJsonToOhlcEntry(JSONObject jsonObject) {
    return OhlcEntry.builder()
        .symbol(jsonObject.getString("symbol"))
        .open(jsonObject.getBigDecimal("open").doubleValue())
        .high(jsonObject.getBigDecimal("high").doubleValue())
        .low(jsonObject.getBigDecimal("low").doubleValue())
        .close(jsonObject.getBigDecimal("close").doubleValue())
        .trades(jsonObject.getInt("trades"))
        .volume(jsonObject.getBigDecimal("volume").doubleValue())
        .vwap(jsonObject.getBigDecimal("vwap").doubleValue())
        .intervalBegin(ZonedDateTime.parse(jsonObject.getString("interval_begin"))
            .withZoneSameInstant(ZoneId.systemDefault()))
        .interval(jsonObject.getInt("interval"))
        .timestamp(ZonedDateTime.parse(jsonObject.getString("timestamp"))
            .withZoneSameInstant(ZoneId.systemDefault()))
        .build();
  }
}
