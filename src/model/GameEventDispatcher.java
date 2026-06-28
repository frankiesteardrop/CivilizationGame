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

    /**
     * حیاتی برای جلوگیری از نشت حافظه هنگام بازگشت به منوی اصلی.
     * باید قبل از ساخت HUDPanel جدید فراخوانی شود.
     */
    public static void clearAllListeners() {
        listeners.clear();
    }

    public static void fireResourceChanged(ResourceType type, int newAmount) {
        // کپی از لیست برای جلوگیری از ConcurrentModificationException
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onResourceChanged(type, newAmount);
        }
    }

    public static void fireUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onUnitMoved(unit, oldQ, oldR, newQ, newR);
        }
    }

    public static void fireUnitKilled(Unit unit) {
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onUnitKilled(unit);
        }
    }

    public static void fireProductionCompleted(String itemName) {
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onProductionCompleted(itemName);
        }
    }

    public static void fireTurnEnded(int newTurn) {
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onTurnEnded(newTurn);
        }
    }

    /**
     * رویداد جدید: ارسال تغییر وضعیت Starvation به تمام لایه‌های View.
     * بلافاصله بعد از تشخیص Starvation در EconomyManager فراخوانی می‌شود.
     */
    public static void fireStarvationChanged(boolean isStarving) {
        for (GameEventListener l : new ArrayList<>(listeners)) {
            l.onStarvationChanged(isStarving);
        }
    }
}