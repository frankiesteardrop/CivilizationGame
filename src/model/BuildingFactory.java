package model;

public class BuildingFactory {
    public static Building createBuilding(BuildingType type) {
        switch (type) {
            case LUMBER_MILL: return new LumberMill();
            case STONE_MINE: return new StoneMine();
            case IRON_MINE: return new IronMine();
            case FARM: return new Farm();
            case STABLE: return new Stable();
            case SETTLEMENT: return new Settlement();
            default: throw new IllegalArgumentException("Unknown building type: " + type);
        }
    }
}