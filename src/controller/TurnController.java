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

        // ─── ۱. تجدید AP یونیت‌ها ────────────────────────────────────────────
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();

                // F-14: Rebellion penalty فقط برای نظامیان و کارگران
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

        gameMap.removeDeadUnits();
        gameMap.incrementTurn();
        gameMap.updateFogOfWar();

        // ─── ۳. Bear AI + بلایای طبیعی ───────────────────────────────────────
        // F-33: DisasterController از GameMap.bearCooldown استفاده می‌کند،
        // پس هر ترن ساختن آن اشکالی ندارد (state در GameMap نگهداری می‌شود).
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // ─── ۴. اطلاع‌رسانی پایان نوبت ──────────────────────────────────────
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());

        // ─── ۵. رفتار قبایل بعد از End Turn بازیکن ──────────────────────────
        mainController.getTribeController().processTribesTurn();

        // ─── ۶. Autosave ─────────────────────────────────────────────────────
        mainController.getSaveLoadController().autosave();
    }
}