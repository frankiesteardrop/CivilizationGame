package model;

public class Settlement extends Building {
    public Settlement() { super(BuildingType.SETTLEMENT.getMaxWorkers()); }
    @Override public BuildingType getType() { return BuildingType.SETTLEMENT; }
}