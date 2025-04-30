package ch.kekelidze.krakentrader.strategy.dto;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;
import org.ta4j.core.Bar;

@Getter
@Builder
public class EvaluationContext {

  private String symbol;
  private int period;
  private List<Bar> bars;
  @Builder.Default
  private Map<String, String> metadata = new HashMap<>();
}
