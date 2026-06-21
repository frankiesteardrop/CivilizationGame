package model;

public enum TerrainType {
    PLAINS(1),
    FOREST(2),
    MOUNTAIN(4),
    MEADOW(1); // هزینه حرکت سبزه زار تو داک مشخص نشده ولی منطقیش همون 1 هست

    private final int movementCost;

    TerrainType(int movementCost) {
        this.movementCost = movementCost;
    }

    public int getMovementCost() {
        return movementCost;
    }
}