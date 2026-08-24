package model;

import java.util.ArrayList;
import java.util.List;

public class GameEventDispatcher {
    private static final List<ResourceListener> resourceListeners = new ArrayList<>();
    private static final List<UnitListener> unitListeners = new ArrayList<>();
    private static final List<BuildingListener> buildingListeners = new ArrayList<>();
    private static final List<TurnListener> turnListeners = new ArrayList<>();
    private static final List<ProductionListener> productionListeners = new ArrayList<>();
    private static final List<MapListener> mapListeners = new ArrayList<>();
    private static final List<DisasterListener> disasterListeners = new ArrayList<>();
    private static final List<CombatListener> combatListeners = new ArrayList<>();
    private static final List<NotificationListener> notificationListeners = new ArrayList<>();

    public static void addListener(Object listener) {
        if (listener instanceof ResourceListener l && !resourceListeners.contains(l)) resourceListeners.add(l);
        if (listener instanceof UnitListener l && !unitListeners.contains(l)) unitListeners.add(l);
        if (listener instanceof BuildingListener l && !buildingListeners.contains(l)) buildingListeners.add(l);
        if (listener instanceof TurnListener l && !turnListeners.contains(l)) turnListeners.add(l);
        if (listener instanceof ProductionListener l && !productionListeners.contains(l)) productionListeners.add(l);
        if (listener instanceof MapListener l && !mapListeners.contains(l)) mapListeners.add(l);
        if (listener instanceof DisasterListener l && !disasterListeners.contains(l)) disasterListeners.add(l);
        if (listener instanceof CombatListener l && !combatListeners.contains(l)) combatListeners.add(l);
        if (listener instanceof NotificationListener l && !notificationListeners.contains(l)) notificationListeners.add(l);
    }

    public static void removeListener(Object listener) {
        if (listener instanceof ResourceListener l) resourceListeners.remove(l);
        if (listener instanceof UnitListener l) unitListeners.remove(l);
        if (listener instanceof BuildingListener l) buildingListeners.remove(l);
        if (listener instanceof TurnListener l) turnListeners.remove(l);
        if (listener instanceof ProductionListener l) productionListeners.remove(l);
        if (listener instanceof MapListener l) mapListeners.remove(l);
        if (listener instanceof DisasterListener l) disasterListeners.remove(l);
        if (listener instanceof CombatListener l) combatListeners.remove(l);
        if (listener instanceof NotificationListener l) notificationListeners.remove(l);
    }

    public static void clearAllListeners() {
        resourceListeners.clear();
        unitListeners.clear();
        buildingListeners.clear();
        turnListeners.clear();
        productionListeners.clear();
        mapListeners.clear();
        disasterListeners.clear();
        combatListeners.clear();
        notificationListeners.clear();
    }

    public static void fireResourceChanged(ResourceType type, int newAmount) {
        for (ResourceListener l : resourceListeners) l.onResourceChanged(type, newAmount);
    }

    public static void fireUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {
        for (UnitListener l : unitListeners) l.onUnitMoved(unit, oldQ, oldR, newQ, newR);
    }

    public static void fireUnitKilled(Unit unit) {
        for (UnitListener l : unitListeners) l.onUnitKilled(unit);
    }

    public static void fireUnitStateChanged(Unit unit) {
        for (UnitListener l : unitListeners) l.onUnitStateChanged(unit);
    }

    public static void fireProductionCompleted(String itemName) {
        for (ProductionListener l : productionListeners) l.onProductionCompleted(itemName);
    }

    public static void fireTurnEnded(int newTurn) {
        for (TurnListener l : turnListeners) l.onTurnEnded(newTurn);
    }

    public static void fireStarvationChanged(boolean isStarving) {
        for (TurnListener l : turnListeners) l.onStarvationChanged(isStarving);
    }

    public static void fireBuildingConstructed(Hex hex) {
        for (BuildingListener l : buildingListeners) l.onBuildingConstructed(hex);
    }

    public static void fireBuildingDestroyed(Hex hex) {
        for (BuildingListener l : buildingListeners) l.onBuildingDestroyed(hex);
    }

    public static void fireBorderExpanded(int centerQ, int centerR) {
        for (MapListener l : mapListeners) l.onBorderExpanded(centerQ, centerR);
    }

    public static void fireDisasterTriggered(String type, Hex center, List<Hex> affected) {
        for (DisasterListener l : disasterListeners) l.onDisasterTriggered(type, center, affected);
    }

    public static void fireCombatTriggered(List<Integer> attackerDice, List<Integer> defenderDice, int atkDmg, int defDmg) {
        for (CombatListener l : combatListeners) l.onCombatTriggered(attackerDice, defenderDice, atkDmg, defDmg);
    }

    public static void fireNotification(String message) {
        for (NotificationListener l : notificationListeners) l.onNotification(message);
    }
}