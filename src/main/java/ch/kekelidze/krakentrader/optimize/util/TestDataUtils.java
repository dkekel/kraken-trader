package ch.kekelidze.krakentrader.optimize.util;

import java.util.List;
import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;

@Component
public class TestDataUtils {

  /**
   * 70% training data
   */
  public List<Bar> getTestData(List<Bar> data) {
    int trainingSize = (int) (data.size() * 0.7);
    return data.subList(0, trainingSize);
  }
}
