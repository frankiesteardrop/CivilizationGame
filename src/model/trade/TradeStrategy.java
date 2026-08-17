package model.trade;
import model.ResourceType;

public interface TradeStrategy {
    int calculateReceivedAmount(int giveAmount, ResourceType getResource, boolean hasTradeBonus);
}