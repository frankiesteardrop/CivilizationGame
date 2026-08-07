package model;

public class UnitFactory {
    public static Unit createUnit(String unitType, int q, int r) {
        switch (unitType) {
            case "WORKER": return new Worker(q, r);
            case "BUILDER": return new Builder(q, r);
            case "EXPLORER": return new Explorer(q, r);
            case "BORDER_EXPANDER": return new BorderExpander(q, r);
            default: throw new IllegalArgumentException("Unknown unit type: " + unitType);
        }
    }
}