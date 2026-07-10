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
            if (u instanceof BorderExpander && !u.isAlive()) continue;
            return true;
        }
        return false;
    }

    public void forceEndTurn() {
        // گام اول: ریست کردن AP تمام یونیت‌ها (کارگران مستقر نیز شارژ استقرار خود را در این مرحله می‌پردازند)
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        // گام دوم: پردازش اقتصاد، کسر هزینه‌های نگهداری (Upkeep) و دریافت وضعیت قحطی
        boolean isStarving = mainController.getEconomyController().processEndTurn(gameMap);
        gameMap.setStarving(isStarving);

        // گام سوم: اعمال دقیق جریمه قحطی (کسر ۱ واحد AP از تمام یونیت‌های زنده) مطابق داک
        if (isStarving) {
            for (Unit unit : gameMap.getUnits()) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        // حذف یونیت‌های مرده و آپدیت ترن
        gameMap.removeDeadUnits();
        gameMap.incrementTurn();
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}