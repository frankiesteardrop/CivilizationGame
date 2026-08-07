package model;

public class Monument extends Building {
    public Monument() { super(BuildingType.MONUMENT.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.MONUMENT; }
}