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
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        boolean isStarving = mainController.getEconomyController().processEndTurn(gameMap);
        gameMap.setStarving(isStarving);

        if (isStarving) {
            for (Unit unit : gameMap.getUnits()) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        gameMap.removeDeadUnits();
        gameMap.incrementTurn();

        gameMap.updateFogOfWar();

        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}