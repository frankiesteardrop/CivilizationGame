package model;

import java.util.ArrayList;
import java.util.List;

public class GameEventDispatcher {
    private static final List<GameEventListener> listeners = new ArrayList<>();

    public static void addListener(GameEventListener listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener);
        }
    }

    public static void removeListener(GameEventListener listener) {
        listeners.remove(listener);
    }

    // حیاتی برای جلوگیری از نشت حافظه هنگام بازگشت به منوی اصلی
    public static void clearAllListeners() {
        listeners.clear();
    }

    public static void fireResourceChanged(ResourceType type, int newAmount) {
        for (GameEventListener l : listeners) l.onResourceChanged(type, newAmount);
    }

    public static void fireUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {
        for (GameEventListener l : listeners) l.onUnitMoved(unit, oldQ, oldR, newQ, newR);
    }

    public static void fireUnitKilled(Unit unit) {
        for (GameEventListener l : listeners) l.onUnitKilled(unit);
    }

    public static void fireProductionCompleted(String itemName) {
        for (GameEventListener l : listeners) l.onProductionCompleted(itemName);
    }

    // متد جدید برای اطلاع‌رسانی پایان نوبت به تمام لایه‌های گرافیکی
    public static void fireTurnEnded(int newTurn) {
        for (GameEventListener l : listeners) l.onTurnEnded(newTurn);
    }
}