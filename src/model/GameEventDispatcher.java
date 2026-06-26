package model;

import java.util.ArrayList;
import java.util.List;

public class GameEventDispatcher {
    private static final List<GameEventListener> listeners = new ArrayList<>();

    public static void addListener(GameEventListener listener) {
        listeners.add(listener);
    }

    public static void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    public static void fireResourceChanged(ResourceType type, int newAmount) {
        for (GameEventListener l : listeners) {
            l.onResourceChanged(type, newAmount);
        }
    }

    public static void fireUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {
        for (GameEventListener l : listeners) {
            l.onUnitMoved(unit, oldQ, oldR, newQ, newR);
        }
    }

    public static void fireUnitKilled(Unit unit) {
        for (GameEventListener l : listeners) {
            l.onUnitKilled(unit);
        }
    }

    public static void fireProductionCompleted(String itemName) {
        for (GameEventListener l : listeners) {
            l.onProductionCompleted(itemName);
        }
    }
}