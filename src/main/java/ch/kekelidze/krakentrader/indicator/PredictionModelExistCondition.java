package ch.kekelidze.krakentrader.indicator;

import java.io.File;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class PredictionModelExistCondition implements Condition {

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var modelFile = new File("model_v4.h5");
    return modelFile.exists();
  }
}
