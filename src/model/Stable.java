package model;

public class Stable extends Building {
    public Stable() { super(BuildingType.STABLE.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.STABLE; }
}