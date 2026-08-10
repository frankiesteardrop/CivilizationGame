package controller;

import model.GameEventDispatcher;
import model.GameMap;
import model.Hex;
import model.Unit;
import model.Worker;

public class TurnController {

    private final MainController mainController;
    private final GameMap gameMap;

    public TurnController(MainController mainController, GameMap gameMap) {
        this.mainController = mainController;
        this.gameMap = gameMap;
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
        int effectiveHappiness = mainController.getEconomyController().getEffectiveHappiness(gameMap);

        // ۱. تجدید AP یونیت‌ها و اعمال پنالتی‌های Happiness
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();
                if (effectiveHappiness <= -5) {
                    unit.consumeAP(1);
                }
            }
        }

        // رفع باگ 24: کاهش تایمر توقف تولید ناشی از سیل در هر نوبت
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        gameMap.removeDeadUnits();
        gameMap.incrementTurn();
        gameMap.updateFogOfWar();

        // اتصال چرخه بلایا به هوش مصنوعی (Bear AI و بلایا)
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // اطلاع‌رسانی پایان نوبت به رویدادها (که EconomyController در اینجا تولید را محاسبه می‌کند)
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());

        mainController.getTribeController().processTribesTurn();

        // عملیات Autosave در پایان تمام رخدادهای ترن
        mainController.getSaveLoadController().autosave();
    }
}