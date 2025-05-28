package ch.kekelidze.krakentrader.api.rest.service;

import ch.kekelidze.krakentrader.api.HistoricalDataService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.ta4j.core.Bar;

@Service
@RequiredArgsConstructor
@Profile("live-data")
public class MarketDataService implements HistoricalDataService {

  private final ResponseConverterUtils responseConverterUtils;

  /**
   * Kraken’s OHLC Format: Each candle is an array [time, open, high, low, close, vwap, volume,
   * count].
   *
   * @param coin   coin to get data for
   * @param period period duration, e.g. 60 for 1-hour candles
   * @return list of closing prices per period
   */
  @Override
  public Map<String, List<Bar>> queryHistoricalData(List<String> coin, int period) {
    var historicalData = new HashMap<String, List<Bar>>();
    try (HttpClient client = HttpClient.newHttpClient()) {
      for (String coinPair : coin) {
        String url =
            "https://api.kraken.com/0/public/OHLC?pair=" + coinPair + "&interval=" + period;
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        JSONObject result = json.getJSONObject("result");
        JSONArray ohlcData = result.keySet().stream().filter(coinPair::equals)
            .map(result::getJSONArray).findFirst().orElse(new JSONArray());

        // Extract closing prices (index 4 in Kraken's OHLC array)
        var dataBars = new ArrayList<Bar>();
        for (int i = 0; i < ohlcData.length(); i++) {
          var ohlcCandle = ohlcData.getJSONArray(i);
          var bar = responseConverterUtils.getPriceBar(ohlcCandle, period);
          dataBars.add(bar);
        }
        historicalData.put(coinPair, dataBars);
      }
      return historicalData;
    } catch (IOException | InterruptedException e) {
      throw new RuntimeException("Failed to fetch and parse historical data: " + e.getMessage(), e);
    }
  }
}
