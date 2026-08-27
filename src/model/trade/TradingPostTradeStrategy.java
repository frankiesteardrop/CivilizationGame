package model.trade;
import model.ResourceType;

public class TradingPostTradeStrategy implements TradeStrategy {
    @Override
    public int calculateReceivedAmount(int giveAmount, ResourceType getResource, boolean hasTradeBonus) {
        double baseRate = 0.80;
        return (int) Math.floor(giveAmount * baseRate);
    }
}