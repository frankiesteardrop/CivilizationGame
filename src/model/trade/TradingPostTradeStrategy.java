package model.trade;
import model.ResourceType;

public class TradingPostTradeStrategy implements TradeStrategy {
    @Override
    public int calculateReceivedAmount(int giveAmount, ResourceType getResource, boolean hasTradeBonus) {
        double effectiveRate = 0.80 * (hasTradeBonus ? 1.10 : 1.00);
        return (int) Math.floor(giveAmount * effectiveRate);
    }
}