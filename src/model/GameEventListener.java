package model;

public interface GameEventListener {
    void onResourceChanged(ResourceType type, int newAmount);
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onProductionCompleted(String itemName);
}