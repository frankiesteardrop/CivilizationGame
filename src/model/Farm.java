package model;

public class Farm extends Building {
    public Farm() { super(BuildingType.FARM.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.FARM; }
}