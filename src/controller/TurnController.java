package controller;

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
        int effectiveHappiness = mainController.getEconomyController().getEffectiveHappiness(gameMap);

        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();
                if (effectiveHappiness <= -5) {
                    unit.consumeAP(1);
                }
            }
        }

        gameMap.removeDeadUnits();
        gameMap.incrementTurn();
        gameMap.updateFogOfWar();

        new DisasterController(gameMap).checkAndTriggerDisasters();

        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());

        mainController.getTribeController().processTribesTurn();

        // عملیات Autosave در پایان تمام رخدادهای ترن
        mainController.getSaveLoadController().autosave();
    }
}