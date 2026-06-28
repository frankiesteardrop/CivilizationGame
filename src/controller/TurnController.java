package controller;

import model.GameEventDispatcher;
import model.GameMap;
import model.Unit;
import model.Worker;

public class TurnController {
    private final GameMap gameMap;

    public TurnController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    /**
     * بررسی وجود یونیت‌های Idle (زنده، دارای AP، غیر-stationed).
     * اصلاح شده: یونیت با AP=0 idle محسوب نمی‌شود حتی اگر stationed نباشد.
     */
    public boolean hasIdleUnits() {
        for (Unit u : gameMap.getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getCurrentAP() <= 0) continue;

            // Worker stationed مشغول کار است — idle نیست
            if (u instanceof Worker && ((Worker) u).isStationed()) continue;

            return true;
        }
        return false;
    }

    /**
     * اجرای قطعی پایان نوبت.
     * ابتدا چرخه بازی را پیش می‌برد، سپس رویداد TurnEnded را می‌زند.
     */
    public void forceEndTurn() {
        gameMap.nextTurn();
        // ارسال رویداد پایان نوبت به تمام listener‌ها (از جمله HUDPanel)
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}