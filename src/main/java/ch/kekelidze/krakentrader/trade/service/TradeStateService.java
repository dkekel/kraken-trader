package ch.kekelidze.krakentrader.trade.service;

import ch.kekelidze.krakentrader.api.rest.service.KrakenAveragePriceService;
import ch.kekelidze.krakentrader.api.rest.service.TradingApiService;
import ch.kekelidze.krakentrader.trade.TradeState;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TradeStateService {
    private static final Map<String, String> ASSET_MAPPINGS = Map.ofEntries(
        Map.entry("XRP.F", "XRP/USD"),
        Map.entry("HONEY", "HONEY/USD"),
        Map.entry("ETH.F", "ETH/USD"),
        Map.entry("XXDG", "DOGE/USD"),
        Map.entry("FLR.S", "FLR/USD"),
        Map.entry("PEPE", "PEPE/USD"),
        Map.entry("SGB", "SGB/USD")
    );

    private final KrakenAveragePriceService averagePriceService;
    private final TradingApiService krakenApiService;
    
    @Autowired
    public TradeStateService(KrakenAveragePriceService averagePriceService,
        TradingApiService krakenApiService) {
        this.averagePriceService = averagePriceService;
      this.krakenApiService = krakenApiService;
    }

    public void displayAveragePrices() {
        try {
            var result = krakenApiService.getAccountBalance();
            var avgPrices = averagePriceService.getAveragePurchasePrices();
            var tradeStates = getTradeStates(result, avgPrices);
            tradeStates.forEach((coinPair, tradeState) -> log.info("{}: {}", coinPair, tradeState));
        } catch (Exception e) {
            log.error("Failed to retrieve average prices", e);
        }
    }

    private static Map<String, TradeState> getTradeStates(Map<String, Double> accountBalances,
        Map<String, Double> averagePurchasePrices) {
        Map<String, TradeState> tradeStates = new HashMap<>();
        accountBalances.forEach((asset, balance) -> {
          var coinPair = ASSET_MAPPINGS.getOrDefault(asset, asset);
          var tradeState = new TradeState(coinPair);
          tradeState.setEntryPrice(averagePurchasePrices.getOrDefault(asset, 0.0));
          tradeState.setPositionSize(balance);
          tradeState.setInTrade(balance > 0);
          tradeStates.put(coinPair, tradeState);
        });
        return tradeStates;
    }
}