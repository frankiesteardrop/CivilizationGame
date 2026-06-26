package model;

public class StoneMine extends Building {
    public StoneMine() { super(BuildingType.STONE_MINE.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.STONE_MINE; }
}