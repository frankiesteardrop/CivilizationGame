package model;

public class BorderExpander extends Unit {
    private static final int EXPAND_AP_COST = 2;

    public BorderExpander(int q, int r) {
        // maxAP=5 (سرعت بالا برای رسیدن به مرزها), foodConsumption=2, visionRadius=2
        super(q, r, 5, 2, 2);
    }

    public boolean canExpand(GameMap map) {
        if (!this.isAlive()) return false;
        if (this.getCurrentAP() < EXPAND_AP_COST) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());
        return currentHex != null && currentHex.isExplored();
    }

    public boolean expandBorder(GameMap map) {
        if (!canExpand(map)) return false;
        this.consumeAP(EXPAND_AP_COST);
        map.expandBorderAt(this.getQ(), this.getR());
        map.updateFogOfWar();
        this.kill();
        return true;
    }

    public static int getExpandApCost() { return EXPAND_AP_COST; }
}