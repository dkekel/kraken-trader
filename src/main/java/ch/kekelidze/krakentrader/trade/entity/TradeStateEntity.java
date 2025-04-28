package ch.kekelidze.krakentrader.trade.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "trade_state")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeStateEntity {

    @Id
    private String coinPair;
    
    private boolean inTrade;
    private double entryPrice;
    private double positionSize;
    private double totalProfit;
}