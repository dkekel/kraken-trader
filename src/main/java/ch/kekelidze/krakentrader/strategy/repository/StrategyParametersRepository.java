package ch.kekelidze.krakentrader.strategy.repository;

import ch.kekelidze.krakentrader.indicator.configuration.entity.StrategyParametersEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for accessing and managing strategy parameters in the database.
 */
@Repository
public interface StrategyParametersRepository extends
    JpaRepository<StrategyParametersEntity, Long> {

  /**
   * Finds strategy parameters for a specific coin pair.
   *
   * @param coinPair the coin pair to find parameters for
   * @return an Optional containing the parameters if found, or empty if not found
   */
  Optional<StrategyParametersEntity> findByCoinPair(String coinPair);

  /**
   * Checks if parameters exist for a specific coin pair.
   *
   * @param coinPair the coin pair to check
   * @return true if parameters exist, false otherwise
   */
  boolean existsByCoinPair(String coinPair);
}