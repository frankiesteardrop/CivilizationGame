package model.trade;
import model.ResourceType;

public class TradingPostTradeStrategy implements TradeStrategy {
    @Override
    public int calculateReceivedAmount(int giveAmount, ResourceType getResource, boolean hasTradeBonus) {
        // مرکز تجارت دارای نرخ ثابت ۸۰٪ است و تحت تاثیر پاداش قبیله قرار نمی‌گیرد
        double baseRate = 0.80;
        return (int) Math.floor(giveAmount * baseRate);
    }
}