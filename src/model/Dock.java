package model;

public class Dock extends Building {
    public Dock() { super(BuildingType.DOCK.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.DOCK; }
}