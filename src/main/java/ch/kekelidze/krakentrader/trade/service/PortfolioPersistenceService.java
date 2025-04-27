package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.trade.entity.PortfolioEntity;
import ch.kekelidze.krakentrader.trade.repository.PortfolioRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PortfolioPersistenceService {

    private static final Long PORTFOLIO_ID = 1L;
    
    private final PortfolioRepository portfolioRepository;

    @Transactional
    public void savePortfolioTotalCapital(double totalCapital) {
        PortfolioEntity entity = portfolioRepository.findById(PORTFOLIO_ID)
                .orElse(PortfolioEntity.builder().id(PORTFOLIO_ID).build());
        
        entity.setTotalCapital(totalCapital);
        portfolioRepository.save(entity);
        log.debug("Saved portfolio total capital to database: {}", totalCapital);
    }

    @Transactional(readOnly = true)
    public Optional<Double> loadPortfolioTotalCapital() {
        return portfolioRepository.findById(PORTFOLIO_ID)
                .map(PortfolioEntity::getTotalCapital);
    }

    @Transactional(readOnly = true)
    public boolean isPortfolioExists() {
        return portfolioRepository.existsById(PORTFOLIO_ID);
    }
}