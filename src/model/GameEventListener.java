package model;

public interface GameEventListener {
    void onResourceChanged(ResourceType type, int newAmount);
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onProductionCompleted(String itemName);
    void onTurnEnded(int newTurn);
    void onStarvationChanged(boolean isStarving);
    void onUnitStateChanged(Unit unit);

    void onBuildingConstructed(Hex hex);
    void onBuildingDestroyed(Hex hex);

    // [اصلاح گام ۶]: قرارداد جدید برای اطلاع‌رسانی از توسعه مرزها
    void onBorderExpanded(int centerQ, int centerR);
}