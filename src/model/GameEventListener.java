package model;

public interface GameEventListener {
    void onResourceChanged(ResourceType type, int newAmount);
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onProductionCompleted(String itemName);
    void onTurnEnded(int newTurn);
    void onStarvationChanged(boolean isStarving);

    // متد جدید برای گام ۵: اطلاع از تغییر وضعیت یونیت (مثل Idle شدن کارگر)
    void onUnitStateChanged(Unit unit);
}