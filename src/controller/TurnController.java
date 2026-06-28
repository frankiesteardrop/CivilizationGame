package controller;

import model.GameMap;
import model.Unit;
import model.Worker;
import model.GameEventDispatcher;

public class TurnController {
    private final GameMap gameMap;

    public TurnController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    // متد کپسوله‌شده برای استعلام گرافیک (بدون هیچ وابستگی به UI)
    public boolean hasIdleUnits() {
        for (Unit u : gameMap.getUnits()) {
            if (u.isAlive() && u.getCurrentAP() > 0) {
                if (u instanceof Worker && ((Worker) u).isStationed()) continue;
                return true;
            }
        }
        return false;
    }

    // اجرای قطعی پایان نوبت
    public void forceEndTurn() {
        gameMap.nextTurn();
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}