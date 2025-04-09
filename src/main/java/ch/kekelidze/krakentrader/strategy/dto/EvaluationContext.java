package ch.kekelidze.krakentrader.strategy.dto;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import org.ta4j.core.Bar;

@Getter
@Builder
public class EvaluationContext {

  private String symbol;
  private List<Bar> bars;
}
