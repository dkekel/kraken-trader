package ch.kekelidze.krakentrader.strategy.service;

import ch.kekelidze.krakentrader.indicator.configuration.StrategyParameters;
import ch.kekelidze.krakentrader.indicator.configuration.entity.StrategyParametersEntity;
import ch.kekelidze.krakentrader.strategy.repository.StrategyParametersRepository;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing strategy parameters in the database.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class StrategyParametersService {

  private final StrategyParametersRepository repository;

  /**
   * Retrieves strategy parameters for a specific coin pair.
   *
   * @param coinPair the coin pair to get parameters for
   * @return an Optional containing the parameters if found, or empty if not found
   */
  public Optional<StrategyParameters> getStrategyParameters(String coinPair) {
    log.debug("Getting strategy parameters for coin pair: {}", coinPair);
    var validCoinPair = getValidCoinName(coinPair);
    Optional<StrategyParametersEntity> entity = repository.findByCoinPair(validCoinPair);

    if (entity.isPresent()) {
      StrategyParameters parameters = entity.get().toStrategyParameters();
      log.info("Loaded strategy parameters for coin pair '{}': {}", coinPair, parameters);
      return Optional.of(parameters);
    } else {
      log.debug("No custom strategy parameters found for coin pair '{}', using default parameters", coinPair);
      return Optional.empty();
    }
  }

  /**
   * Gets the strategy name for a specific coin pair.
   *
   * @param coinPair the coin pair to get the strategy name for
   * @return an Optional containing the strategy name if found, or empty if not found
   */
  public Optional<String> getStrategyName(String coinPair) {
    log.debug("Getting strategy name for coin pair: {}", coinPair);
    var validCoinPair = getValidCoinName(coinPair);
    return repository.findByCoinPair(validCoinPair)
        .map(StrategyParametersEntity::getStrategyName);
  }

  /**
   * Gets all coin pairs that use a specific strategy.
   *
   * @param strategyName the strategy name to find coin pairs for
   * @return a list of coin pairs that use the specified strategy
   */
  public List<String> getCoinPairsByStrategy(String strategyName) {
    log.debug("Getting coin pairs for strategy: {}", strategyName);
    return repository.findByStrategyName(strategyName).stream()
        .map(StrategyParametersEntity::getCoinPair)
        .collect(Collectors.toList());
  }

  /**
   * Saves strategy parameters for a specific coin pair. If parameters already exist for the coin
   * pair, they will be updated.
   *
   * @param coinPair   the coin pair to save parameters for
   * @param parameters the parameters to save
   * @return the saved parameters
   */
  @Transactional
  public StrategyParameters saveStrategyParameters(String coinPair, StrategyParameters parameters) {
    return saveStrategyParameters(coinPair, null, parameters);
  }

  /**
   * Saves strategy parameters for a specific coin pair with a specified strategy name. If
   * parameters already exist for the coin pair, they will be updated.
   *
   * @param coinPair     the coin pair to save parameters for
   * @param strategyName the name of the strategy to use for this coin pair
   * @param parameters   the parameters to save
   * @return the saved parameters
   */
  @Transactional
  public StrategyParameters saveStrategyParameters(String coinPair, String strategyName,
      StrategyParameters parameters) {
    log.debug("Saving strategy parameters for coin pair: {} with strategy: {}", coinPair,
        strategyName);
    var validCoinPair = getValidCoinName(coinPair);

    StrategyParametersEntity entity;
    if (repository.existsByCoinPair(validCoinPair)) {
      entity = repository.findByCoinPair(validCoinPair).orElseThrow();
      // Update existing entity with new parameters
      entity = updateEntityFromParameters(entity, parameters);
      // Update strategy name if provided
      if (strategyName != null) {
        entity.setStrategyName(strategyName);
      }
    } else {
      entity = StrategyParametersEntity.fromStrategyParameters(validCoinPair, strategyName,
          parameters);
    }

    return repository.save(entity).toStrategyParameters();
  }

  /**
   * Updates an existing entity with new parameter values.
   *
   * @param entity     the entity to update
   * @param parameters the new parameters
   * @return the updated entity
   */
  private StrategyParametersEntity updateEntityFromParameters(StrategyParametersEntity entity,
      StrategyParameters parameters) {
    entity.setMovingAverageBuyShortPeriod(parameters.movingAverageBuyShortPeriod());
    entity.setMovingAverageBuyLongPeriod(parameters.movingAverageBuyLongPeriod());
    entity.setMovingAverageSellShortPeriod(parameters.movingAverageSellShortPeriod());
    entity.setMovingAverageSellLongPeriod(parameters.movingAverageSellLongPeriod());
    entity.setRsiPeriod(parameters.rsiPeriod());
    entity.setRsiBuyThreshold(parameters.rsiBuyThreshold());
    entity.setRsiSellThreshold(parameters.rsiSellThreshold());
    entity.setMacdFastPeriod(parameters.macdFastPeriod());
    entity.setMacdSlowPeriod(parameters.macdSlowPeriod());
    entity.setMacdSignalPeriod(parameters.macdSignalPeriod());
    entity.setVolumePeriod(parameters.volumePeriod());
    entity.setAboveAverageThreshold(parameters.aboveAverageThreshold());
    entity.setLossPercent(parameters.lossPercent());
    entity.setProfitPercent(parameters.profitPercent());
    entity.setAdxPeriod(parameters.adxPeriod());
    entity.setAdxBullishThreshold(parameters.adxBullishThreshold());
    entity.setAdxBearishThreshold(parameters.adxBearishThreshold());
    entity.setVolatilityPeriod(parameters.volatilityPeriod());
    entity.setContractionThreshold(parameters.contractionThreshold());
    entity.setLowVolatilityThreshold(parameters.lowVolatilityThreshold());
    entity.setHighVolatilityThreshold(parameters.highVolatilityThreshold());
    entity.setMfiOverboughtThreshold(parameters.mfiOverboughtThreshold());
    entity.setMfiOversoldThreshold(parameters.mfiOversoldThreshold());
    entity.setMfiPeriod(parameters.mfiPeriod());
    entity.setAtrPeriod(parameters.atrPeriod());
    entity.setAtrThreshold(parameters.atrThreshold());
    entity.setLookbackPeriod(parameters.lookbackPeriod());
    entity.setSupportResistancePeriod(parameters.supportResistancePeriod());
    entity.setSupportResistanceThreshold(parameters.supportResistanceThreshold());
    entity.setMinimumCandles(parameters.minimumCandles());
    return entity;
  }

  /**
   * Gets all strategy parameters stored in the database.
   *
   * @return a list of all strategy parameters
   */
  public List<StrategyParameters> getAllStrategyParameters() {
    log.debug("Getting all strategy parameters");
    return repository.findAll().stream()
        .map(StrategyParametersEntity::toStrategyParameters)
        .collect(Collectors.toList());
  }

  /**
   * Deletes strategy parameters for a specific coin pair.
   *
   * @param coinPair the coin pair to delete parameters for
   */
  @Transactional
  public void deleteStrategyParameters(String coinPair) {
    log.debug("Deleting strategy parameters for coin pair: {}", coinPair);
    var validCoinPair = getValidCoinName(coinPair);
    repository.findByCoinPair(validCoinPair).ifPresent(repository::delete);
  }

  private static String getValidCoinName(String coinPair) {
    if (coinPair != null && !coinPair.contains("/") && coinPair.endsWith("USD")) {
      return coinPair.substring(0, coinPair.length() - 3) + "/" + coinPair.substring(
          coinPair.length() - 3);
    }
    return coinPair;
  }
}
