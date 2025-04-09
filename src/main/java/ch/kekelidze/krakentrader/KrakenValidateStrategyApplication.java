package ch.kekelidze.krakentrader;

import ch.kekelidze.krakentrader.api.rest.service.KrakenApiService;
import ch.kekelidze.krakentrader.api.util.ResponseConverterUtils;
import ch.kekelidze.krakentrader.backtester.service.BackTesterService;
import ch.kekelidze.krakentrader.indicator.Indicator;
import ch.kekelidze.krakentrader.strategy.Strategy;
import ch.kekelidze.krakentrader.strategy.dto.EvaluationContext;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@Slf4j
@SpringBootApplication(
    scanBasePackageClasses = {KrakenApiService.class, ResponseConverterUtils.class,
        BackTesterService.class, Indicator.class, Strategy.class}
)
public class KrakenValidateStrategyApplication {

  private static final double INITIAL_CAPITAL = 10000;

  public static void main(String[] args) {
    String coin = args[0];
    int period = Integer.parseInt(args[1]);
    var application = SpringApplication.run(KrakenValidateStrategyApplication.class, args);
    validateWithRecentData(application, coin, period);
  }

  private static void validateWithRecentData(ApplicationContext application, String coin,
      int period) {
    var krakenApiService = application.getBean(KrakenApiService.class);
    var backtestService = application.getBean(BackTesterService.class);
    var historicalData = krakenApiService.queryHistoricalData(List.of(coin), period);
    var evaluationContext = EvaluationContext.builder().symbol(coin).bars(historicalData.get(coin))
        .build();
    var result = backtestService.runSimulation(evaluationContext, INITIAL_CAPITAL);
    log.info("Trade result: {}", result);
  }
}
