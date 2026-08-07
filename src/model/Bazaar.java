package model;

public class Bazaar extends Building {
    private boolean hasTradedThisTurn;
    public Bazaar() { super(BuildingType.BAZAAR.getMaxWorkers()); this.hasTradedThisTurn = false; }
    @Override public BuildingType getType() { return BuildingType.BAZAAR; }
    public boolean hasTraded() { return hasTradedThisTurn; }
    public void setTraded(boolean traded) { this.hasTradedThisTurn = traded; }
}