package ch.kekelidze.krakentrader.api;

import java.util.List;
import java.util.Map;
import org.ta4j.core.Bar;

public interface HistoricalDataService {

  Map<String, List<Bar>> queryHistoricalData(List<String> coin, int period);
}
