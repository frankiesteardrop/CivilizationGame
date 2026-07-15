package model;

public enum TerrainType {
   PLAINS(1),
   FOREST(2),
   MOUNTAIN(4),
   MEADOW(1);

  private final int movementCost;

   TerrainType(int movementCost) {
        this.movementCost = movementCost;
    }

  public int getMovementCost() {
       return movementCost;
    }
}