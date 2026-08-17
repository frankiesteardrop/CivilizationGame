package model.trade;
import model.ResourceType;

public class BazaarTradeStrategy implements TradeStrategy {
    private final int level;

    public BazaarTradeStrategy(int level) {
        this.level = level;
    }

    @Override
    public int calculateReceivedAmount(int giveAmount, ResourceType getResource, boolean hasTradeBonus) {
        double baseRate = (level == 1) ? 0.5 : (level == 2) ? 0.6 : 0.7;
        // اتحاد با قبیله تجاری ۱۰٪ بونوس دارد
        double effectiveRate = baseRate * (hasTradeBonus ? 1.10 : 1.00);
        return (int) Math.floor(giveAmount * effectiveRate);
    }
}