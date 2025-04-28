package ch.kekelidze.krakentrader.trade.repository;

import ch.kekelidze.krakentrader.trade.entity.TradeStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TradeStateRepository extends JpaRepository<TradeStateEntity, String> {
}