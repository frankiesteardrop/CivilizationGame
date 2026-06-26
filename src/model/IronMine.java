package model;

public class IronMine extends Building {
    public IronMine() { super(BuildingType.IRON_MINE.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.IRON_MINE; }
}