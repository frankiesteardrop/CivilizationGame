package controller;

import model.BorderExpander;
import model.GameEventDispatcher;
import model.GameMap;
import model.Unit;
import model.Worker;


public class TurnController {

    private final GameMap gameMap;

    public TurnController(GameMap gameMap) {
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
        gameMap.nextTurn();
        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());
    }
}