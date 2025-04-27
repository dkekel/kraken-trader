package ch.kekelidze.krakentrader.trade.repository;

import ch.kekelidze.krakentrader.trade.entity.PortfolioEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PortfolioRepository extends JpaRepository<PortfolioEntity, Long> {
}