package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.trade.TradeState;
import ch.kekelidze.krakentrader.trade.entity.TradeStateEntity;
import ch.kekelidze.krakentrader.trade.repository.TradeStateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TradeStatePersistenceService {

    private final TradeStateRepository tradeStateRepository;

    @Transactional
    public void saveTradeState(TradeState tradeState) {
        TradeStateEntity entity = convertToEntity(tradeState);
        tradeStateRepository.save(entity);
        log.debug("Saved trade state to database for {}: {}", tradeState.getCoinPair(), tradeState);
    }

    @Transactional(readOnly = true)
    public Optional<TradeState> loadTradeState(String coinPair) {
        return tradeStateRepository.findById(coinPair)
                .map(this::convertToTradeState);
    }

    private TradeStateEntity convertToEntity(TradeState tradeState) {
        return TradeStateEntity.builder()
                .coinPair(tradeState.getCoinPair())
                .inTrade(tradeState.isInTrade())
                .entryPrice(tradeState.getEntryPrice())
                .positionSize(tradeState.getPositionSize())
                .totalProfit(tradeState.getTotalProfit())
                .build();
    }

    private TradeState convertToTradeState(TradeStateEntity entity) {
        TradeState tradeState = new TradeState(entity.getCoinPair());
        tradeState.setInTrade(entity.isInTrade());
        tradeState.setEntryPrice(entity.getEntryPrice());
        tradeState.setPositionSize(entity.getPositionSize());
        tradeState.setTotalProfit(entity.getTotalProfit());
        return tradeState;
    }
}
