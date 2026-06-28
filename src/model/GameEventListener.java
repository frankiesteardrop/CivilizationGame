package model;

public interface GameEventListener {
    void onResourceChanged(ResourceType type, int newAmount);
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onProductionCompleted(String itemName);
    void onTurnEnded(int newTurn);
    void onStarvationChanged(boolean isStarving);
    void onUnitStateChanged(Unit unit);

    // [اضافه شده برای گام ۶]: اطلاع از احداث یک ساختمان جدید
    void onBuildingConstructed(Hex hex);
}