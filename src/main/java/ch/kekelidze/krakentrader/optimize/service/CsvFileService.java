package ch.kekelidze.krakentrader.optimize.service;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.ta4j.core.BaseBar;
import org.ta4j.core.Bar;
import org.ta4j.core.num.DecimalNum;

@Slf4j
@Service
public class CsvFileService {

  /**
   * Kraken’s OHLC Format: Each candle is an array [time, open, high, low, close, volume, count].
   * @return parsed historical data file
   */
  public List<Bar> readCsvFile(String filePath) {
    List<Bar> bars = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String[] fields = line.split(",");
        if (fields.length != 7) {
          log.warn("Invalid row in CSV file: {}", line);
          continue;
        }

        long epochTime = Long.parseLong(fields[0]);
        var open = DecimalNum.valueOf(fields[1]);
        var high = DecimalNum.valueOf(fields[2]);
        var low = DecimalNum.valueOf(fields[3]);
        var close = DecimalNum.valueOf(fields[4]);
        var volume = DecimalNum.valueOf(fields[6]);

        Bar bar = BaseBar.builder(DecimalNum::valueOf, Number.class)
            .timePeriod(Duration.ofMinutes(60))
            .endTime(ZonedDateTime.ofInstant(
                Instant.ofEpochSecond(epochTime),
                ZoneId.systemDefault()))
            .openPrice(open)
            .highPrice(high)
            .lowPrice(low)
            .closePrice(close)
            .volume(volume)
            .build();
        bars.add(bar);
      }
    } catch (IOException e) {
      throw new RuntimeException("Failed to read CSV file: " + filePath, e);
    }
    return bars;
  }
}
