package model;

public class TradingPost extends Building {
    private boolean hasTradedThisTurn;
    public TradingPost() { super(BuildingType.TRADING_POST.getMaxWorkers()); this.hasTradedThisTurn = false; }
    @Override public BuildingType getType() { return BuildingType.TRADING_POST; }
    public boolean hasTraded() { return hasTradedThisTurn; }
    public void setTraded(boolean traded) { this.hasTradedThisTurn = traded; }
}