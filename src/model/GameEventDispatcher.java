package model;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList; // B22: thread-safe list

/**
 * Central event bus for the game engine.
 *
 * <p>All listener lists use {@link CopyOnWriteArrayList} (B22) so that
 * the server's multiple threads can call fire*() methods concurrently without
 * causing {@link java.util.ConcurrentModificationException} or data races.
 * CopyOnWriteArrayList is ideal here because:
 * <ul>
 *   <li>Reads (iteration during fire*()) are lock-free and very fast.</li>
 *   <li>Writes (add/remove listeners) are rare and can tolerate copy overhead.</li>
 * </ul>
 */
public class GameEventDispatcher {

    // B22: CopyOnWriteArrayList replaces ArrayList for thread-safety on multi-threaded server
    private static final List<ResourceListener>     resourceListeners    =    private static final List<ResourceListener>     resourceListeners    =
            new CopyOnWriteArrayList<>();
    private static final List<UnitListener>         unitListeners        =
            new CopyOnWriteArrayList<>();
    private static final List<BuildingListener>     buildingListeners    =
            new CopyOnWriteArrayList<>();
    private static final List<TurnListener>         turnListeners        =
            new CopyOnWriteArrayList<>();
    private static final List<ProductionListener>   productionListeners  =
            new CopyOnWriteArrayList<>();
    private static final List<MapListener>          mapListeners         =
            new CopyOnWriteArrayList<>();
    private static final List<NotificationListener> notificationListeners=
            new CopyOnWriteArrayList<>();
    private static final List<CombatListener>       combatListeners      =
            new CopyOnWriteArrayList<>();
    private static final List<DisasterListener>     disasterListeners    =
            new CopyOnWriteArrayList<>();

    private GameEventDispatcher() {}

    // ─── Registration ─────────────────────────────────────────────────────────

    public static void addListener(Object listener) {
        if (listener instanceof ResourceListener l
                && !resourceListeners.contains(l))     resourceListeners.add(l);
        if (listener instanceof UnitListener l
                && !unitListeners.contains(l))         unitListeners.add(l);
        if (listener instanceof BuildingListener l
                && !buildingListeners.contains(l))     buildingListeners.add(l);
        if (listener instanceof TurnListener l
                && !turnListeners.contains(l))         turnListeners.add(l);
        if (listener instanceof ProductionListener l
                && !productionListeners.contains(l))   productionListeners.add(l);
        if (listener instanceof MapListener l
                && !mapListeners.contains(l))          mapListeners.add(l);
        if (listener instanceof NotificationListener l
                && !notificationListeners.contains(l)) notificationListeners.add(l);
        if (listener instanceof CombatListener l
                && !combatListeners.contains(l))       combatListeners.add(l);
        if (listener instanceof DisasterListener l
                && !disasterListeners.contains(l))     disasterListeners.add(l);
    }

    public static void removeListener(Object listener) {
        resourceListeners.remove(listener);
        unitListeners.remove(listener);
        buildingListeners.remove(listener);
        turnListeners.remove(listener);
        productionListeners.remove(listener);
        mapListeners.remove(listener);
        notificationListeners.remove(listener);
        combatListeners.remove(listener);
        disasterListeners.remove(listener);
    }

    public static void clearAllListeners() {
        resourceListeners.clear();
        unitListeners.clear();
        buildingListeners.clear();
        turnListeners.clear();
        productionListeners.clear();
        mapListeners.clear();
        notificationListeners.clear();
        combatListeners.clear();
        disasterListeners.clear();
    }

    // ─── Fire methods ─────────────────────────────────────────────────────────
    // CopyOnWriteArrayList iteration is always safe — no locking needed here.

    public static void fireResourceChanged(ResourceType type, int newAmount) {
        for (ResourceListener l : resourceListeners) l.onResourceChanged(type, newAmount);
    }

    public static void fireUnitMoved(Unit unit, int oq, int or_, int nq, int nr) {
        for (UnitListener l : unitListeners) l.onUnitMoved(unit, oq, or_, nq, nr);
    }

    public static void fireUnitKilled(Unit unit) {
        for (UnitListener l : unitListeners) l.onUnitKilled(unit);
    }

    public static void fireUnitStateChanged(Unit unit) {
        for (UnitListener l : unitListeners) l.onUnitStateChanged(unit);
    }

    public static void fireBuildingConstructed(Hex hex) {
        for (BuildingListener l : buildingListeners) l.onBuildingConstructed(hex);
    }

    public static void fireBuildingDestroyed(Hex hex) {
        for (BuildingListener l : buildingListeners) l.onBuildingDestroyed(hex);
    }

    public static void fireTurnEnded(int newTurn) {
        for (TurnListener l : turnListeners) l.onTurnEnded(newTurn);
    }

    public static void fireStarvationChanged(boolean isStarving) {
        for (TurnListener l : turnListeners) l.onStarvationChanged(isStarving);
    }

    public static void fireProductionCompleted(String itemName) {
        for (ProductionListener l : productionListeners) l.onProductionCompleted(itemName);
    }

    public static void fireBorderExpanded(int q, int r) {
        for (MapListener l : mapListeners) l.onBorderExpanded(q, r);
    }

    public static void fireNotification(String message) {
        for (NotificationListener l : notificationListeners) l.onNotification(message);
    }

    public static void fireCombatTriggered(List<Integer> atkDice, List<Integer> defDice,
                                           int atkDmg, int defDmg) {
        for (CombatListener l : combatListeners)
            l.onCombatTriggered(atkDice, defDice, atkDmg, defDmg);
    }

    public static void fireDisasterTriggered(String type, Hex center, List<Hex> affected) {
        for (DisasterListener l : disasterListeners)
            l.onDisasterTriggered(type, center, affected);
    }
}