package network.server;

import controller.DisasterController;
import controller.EconomyController;
import controller.TribeController;
import model.*;

import java.util.List;

public class ServerTurnProcessor {

    private final EconomyController economyController;
    private final TribeController   tribeController;

    public ServerTurnProcessor(GameMap gameMap) {
        this.economyController = new EconomyController();
        this.tribeController   = new TribeController(gameMap);
    }

    public void processPlayerTurnEnd(GameMap gameMap, String clientId) {
        TownHall playerTH = gameMap.getPlayerTownHall(clientId);

        boolean isStarving = false;
        if (playerTH != null) {
            gameMap.setActiveTownHall(playerTH);
            isStarving = economyController.processEndTurn(gameMap);
            playerTH.advanceProductionQueue(isStarving);
            gameMap.clearActiveTownHall();
        }

        int effectiveHappiness = economyController.getEffectiveHappiness(gameMap);

        for (Unit unit : gameMap.getUnits()) {
            if (!unit.isAlive() || !clientId.equals(unit.getOwnerId())) continue;

            unit.resetAP();
            unit.resetItemBuffs();

            if (effectiveHappiness <= -5) {
                UnitType t = unit.getType();
                if (t == UnitType.WORKER || t == UnitType.SWORDSMAN
                        || t == UnitType.ARCHER || t == UnitType.CAVALRY
                        || t == UnitType.CATAPULT) {
                    unit.consumeAP(1);
                }
            }

            if (isStarving) {
                unit.consumeAP(1);
            }
        }

        for (Hex hex : gameMap.getHexes()) {
            if (!(hex.getBuilding() instanceof Apothecary apothecary)) continue;
            if (apothecary.isDestroyed()) continue;
            if (!clientId.equals(apothecary.getOwnerId())) continue;

            String completedItem = apothecary.advanceCraftingQueue();
            if (completedItem == null) continue;

            Empire ownerEmpire = gameMap.getEmpire(clientId);
            if (ownerEmpire != null) {
                ownerEmpire.getInventory().addItem(completedItem, 1);
            }
        }

        gameMap.removeDeadUnits();
    }

    public void processGlobalRoundEnd(GameMap gameMap) {
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        gameMap.removeDeadUnits();

        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        tribeController.processTribesTurn();

        for (Unit unit : gameMap.getUnits()) {
            if (!unit.isAlive()) continue;
            if (unit.getType() == UnitType.BEAR) {
                unit.resetAP();
            }
        }

        gameMap.removeDeadUnits();
    }
}