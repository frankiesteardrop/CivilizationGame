package model;

public enum TerrainType {
    PLAINS(1),
    FOREST(2),
    MOUNTAIN(4),
    MEADOW(1),
    SEA(9999),
    MOUNTAIN_RANGE(9999);

    private final int movementCost;

    TerrainType(int movementCost) {
        this.movementCost = movementCost;
    }

    public int getMovementCost() {
        return movementCost;
    }
}