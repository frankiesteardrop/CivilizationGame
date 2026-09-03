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
        mainController.setProcessingTurn(true);
        try {
            executeEndTurnLogic();
        } finally {
            mainController.setProcessingTurn(false);
        }
    }

    private void executeEndTurnLogic() {
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        gameMap.removeDeadUnits();

        Season seasonBefore = gameMap.getCurrentSeason();
        gameMap.incrementTurn();
        gameMap.updateFogOfWar();
        Season seasonAfter = gameMap.getCurrentSeason();
        if (seasonBefore != seasonAfter) {
            fireSeasonChangeNotification(seasonAfter);
        }

        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        GameEventDispatcher.fireTurnEnded(gameMap.getCurrentTurn());

        mainController.getTribeController().processTribesTurn();

        int effectiveHappiness = mainController.getEconomyController().getEffectiveHappiness(gameMap);
        for (Unit unit : gameMap.getUnits()) {
            if (unit.isAlive() && !unit.isEnemy() && unit.getType() != UnitType.BEAR) {

                unit.resetAP();
                unit.resetItemBuffs(); // پاک کردن باف آیتم‌ها در پایان نوبت

                if (effectiveHappiness <= -5) {
                    UnitType t = unit.getType();
                    if (t == UnitType.WORKER || t == UnitType.SWORDSMAN
                            || t == UnitType.ARCHER || t == UnitType.CAVALRY) {
                        unit.consumeAP(1);
                    }
                }

                if (gameMap.isStarving()) {
                    unit.consumeAP(1);
                }
            } else if (unit.isAlive() && unit.getType() == UnitType.BEAR) {
                unit.resetAP();
            }
        }

        mainController.getSaveLoadController().autosave();
    }

    private void fireSeasonChangeNotification(Season newSeason) {
        String message = switch (newSeason) {
            case SPRING -> "🌸 Spring has arrived!";
            case SUMMER -> "☀️ Summer begins!";
            case AUTUMN -> "🍂 Autumn is here!";
            case WINTER -> "❄️ Winter has come!";
        };
        GameEventDispatcher.fireNotification(message);
    }
}