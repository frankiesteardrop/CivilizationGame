package model;

public class BorderExpander extends Unit {

    public BorderExpander(int q, int r) {
        // maxAP=5 (سرعت بالا برای رسیدن به مرزها), foodConsumption=2, visionRadius=2
        super(q, r, 5, 2, 2);
    }

    public boolean canExpand(GameMap map) {
        if (!this.isAlive()) return false;

        // استفاده مستقیم از تنها منبع حقیقت برای رفع باگ ۸
        if (this.getCurrentAP() < GameConfig.EXPAND_AP_COST) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());
        return currentHex != null && currentHex.isExplored();
    }

    // متد expandBorder که یک Dead Code خطرناک بود کاملاً پاکسازی شد (رفع باگ ۷)

    public static int getExpandApCost() {
        return GameConfig.EXPAND_AP_COST;
    }
}