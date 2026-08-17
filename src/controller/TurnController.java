package controller;

import model.*;

public class TurnController {

    private final MainController mainController;
    private final GameMap        gameMap;

    public TurnController(MainController mainController, GameMap gameMap) {
        this.mainController = mainController;
        this.gameMap        = gameMap;
    }

    public boolean hasIdleUnits() {
        for (Unit u : gameMap.getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getCurrentAP() <= 0) continue;
            if (u instanceof Worker && ((Worker) u).isStationed()) continue;
            return true;
        }
        return false;
    }

    public void forceEndTurn() {
        int effectiveHappiness = mainController.getEconomyController()
                .getEffectiveHappiness(gameMap);

        // ─── ۱. تجدید AP یونیت‌ها و اعمال جریمه شورش (Rebellion) ───
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();

                if (effectiveHappiness <= -5) {
                    UnitType t = unit.getType();
                    if (t == UnitType.WORKER    || t == UnitType.SWORDSMAN
                            || t == UnitType.ARCHER    || t == UnitType.CAVALRY) {
                        unit.consumeAP(1);
                    }
                }
            }
        }

        // ─── ۲. کاهش تایمر توقف تولید سیل ──────────────────────────────────
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        // ─── ۳. پاکسازی ایمن واحدهای مرده ───────────────────────────────────
        gameMap.removeDeadUnits();
        gameMap.incrementTurn();
        gameMap.updateFogOfWar();

        // ─── ۴. هوش مصنوعی خرس و بلایای طبیعی ─────────────────────────────
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // ─── ۵. اطلاع‌رسانی پایان نوبت و رفتار قبایل ────────────────────────
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
        mainController.getTribeController().processTribesTurn();

        // ─── ۶. ذخیره خودکار (Autosave) ───────────────────────────────────
        mainController.getSaveLoadController().autosave();
    }
}