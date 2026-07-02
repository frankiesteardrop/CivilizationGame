package model;

public class BorderExpander extends Unit {

    public BorderExpander(int q, int r) {
        // [گام حل باگ ۱۳]: حذف اعداد هاردکد شده و ارسال نوع یونیت
        super(q, r, UnitType.BORDER_EXPANDER);
    }

    public boolean canExpand(GameMap map) {
        if (!this.isAlive()) return false;

        if (this.getCurrentAP() < GameConfig.EXPAND_AP_COST) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());
        return currentHex != null && currentHex.isExplored();
    }

    public static int getExpandApCost() {
        return GameConfig.EXPAND_AP_COST;
    }
}