package controller;

import model.BorderExpander;
import model.GameEventDispatcher;
import model.GameMap;
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
        // گام اول: تجدید AP تمام یونیت‌ها
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        // گام دوم و سوم: تولید منابع، پیشروی صف، کسر Upkeep و بررسی قحطی
        boolean isStarving = mainController.getEconomyController().processEndTurn(gameMap);
        gameMap.setStarving(isStarving);

        // اعمال ضعف و جریمه قحطی روی یونیت‌ها
        if (isStarving) {
            for (Unit unit : gameMap.getUnits()) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        gameMap.removeDeadUnits();
        gameMap.incrementTurn();

        // رفع باگ State Desync: آپدیت اجباری مه‌جنگ برای اعمال تاثیراتِ
        // ساختمان‌های مخروبه شده و یونیت‌های تازه تولید شده در این ترن
        gameMap.updateFogOfWar();

        // بازگشت کنترل به بازیکن برای شروع نوبت جدید
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}