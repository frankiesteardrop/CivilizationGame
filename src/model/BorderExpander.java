package model;

public class BorderExpander extends Unit {

    public BorderExpander(int q, int r) {
        super(q, r, UnitType.BORDER_EXPANDER);
    }

    public boolean canExpand(GameMap map) {
        if (!this.isAlive()) return false;

        if (this.getCurrentAP() < GameConfig.EXPAND_AP_COST) return false;

        Hex currentHex = map.getHexAt(this.getQ(), this.getR());
        if (currentHex == null || !currentHex.isExplored()) return false;

        return map.isContiguousToBorder(this.getQ(), this.getR());
    }

    public static int getExpandApCost() {
        return GameConfig.EXPAND_AP_COST;
    }
}