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
    void onBorderExpanded(int centerQ, int centerR);

    // رویدادهای جدید گرافیکی برای نبرد و بلایا
    void onDisasterTriggered(String type, Hex center, java.util.List<Hex> affected);
    void onCombatTriggered(java.util.List<Integer> attackerDice, java.util.List<Integer> defenderDice, int atkDmg, int defDmg);
}