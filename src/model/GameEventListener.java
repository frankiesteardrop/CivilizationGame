package model;

public interface GameEventListener {
    void onResourceChanged(ResourceType type, int newAmount);
    void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR);
    void onUnitKilled(Unit unit);
    void onProductionCompleted(String itemName);
    void onTurnEnded(int newTurn);

    /**
     * رویداد جدید: اطلاع‌رسانی تغییر وضعیت Starvation به لایه View.
     * @param isStarving true اگر بازی وارد فاز قحطی شده باشد
     */
    void onStarvationChanged(boolean isStarving);
}