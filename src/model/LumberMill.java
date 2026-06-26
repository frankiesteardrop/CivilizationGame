package model;

public class LumberMill extends Building {
    public LumberMill() { super(BuildingType.LUMBER_MILL.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.LUMBER_MILL; }
}