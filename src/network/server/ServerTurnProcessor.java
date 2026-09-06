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

    /**
     * Executes end-of-turn processing for a SINGLE player.
     * (AP reset, item buff clear, individual queue advance)
     */
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
            unit.resetItemBuffs(); // clear teleport/mobility/combat item buffs

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

        gameMap.removeDeadUnits();
    }

    /**
     * Executes end-of-round processing for ALL players globally.
     * (Disasters, Tribes, Bear AI, global item crafting)
     */
    public void processGlobalRoundEnd(GameMap gameMap) {
        // Step 1: decrement flood-halt timers
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        gameMap.removeDeadUnits();

        // Step 2: natural disasters & bear AI
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // Step 3: tribe AI
        tribeController.processTribesTurn();

        // Step 4: Apothecary crafting queues
        for (Hex hex : gameMap.getHexes()) {
            if (!(hex.getBuilding() instanceof Apothecary apothecary)) continue;
            if (apothecary.isDestroyed()) continue;

            String completedItem = apothecary.advanceCraftingQueue();
            if (completedItem == null) continue;

            String ownerId = apothecary.getOwnerId();
            if (ownerId == null) continue;

            TownHall ownerTH = gameMap.getPlayerTownHall(ownerId);
            if (ownerTH != null) {
                ownerTH.getInventory().addItem(completedItem, 1);
                GameEventDispatcher.fireNotification("⚗️ Apothecary finished crafting: "
                        + completedItem + "! Added to your inventory.");
            }
        }

        for (Unit unit : gameMap.getUnits()) {
            if (!unit.isAlive()) continue;
            if (unit.getType() == UnitType.BEAR) {
                unit.resetAP();
            }
        }

        gameMap.removeDeadUnits();
    }
}