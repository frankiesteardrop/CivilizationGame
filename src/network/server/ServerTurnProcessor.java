package network.server;

import controller.DisasterController;
import controller.EconomyController;
import controller.TribeController;
import model.*;

import java.util.List;

/**
 * Handles all server-side end-of-turn game logic for multiplayer sessions.
 *
 * <p>B33 fix: in multiplayer, each player has their own TownHall with their own
 * Inventory and production queue. Economy processing is done per-player by
 * temporarily setting {@link GameMap#setActiveTownHall(TownHall)} so that
 * {@link EconomyController} operates on the correct player's resources.
 *
 * <p>Processing order per turn:
 * <ol>
 *   <li>Decrement flood-halt timers</li>
 *   <li>Remove dead units</li>
 *   <li>Bear AI + disaster checks</li>
 *   <li>Tribe AI turn behavior</li>
 *   <li>Per-player economy (resource production, upkeep, food consumption) — B33</li>
 *   <li>Per-player production queue advance — B33</li>
 *   <li>Apothecary crafting queue advance (B11)</li>
 *   <li>Per-unit AP reset + happiness/starvation penalties</li>
 *   <li>Remove units that died from starvation</li>
 * </ol>
 */
public class ServerTurnProcessor {

    private final EconomyController economyController;
    private final TribeController   tribeController;

    public ServerTurnProcessor(GameMap gameMap) {
        this.economyController = new EconomyController();   // server constructor (no MainController)
        this.tribeController   = new TribeController(gameMap);
    }

    /**
     * Executes all end-of-turn processing on the master map.
     * Called by {@link GameStateManager} each time the active player clicks "End Turn".
     */
    public void processTurn(GameMap gameMap) {

        // ── Step 1: decrement flood-halt timers ───────────────────────────────
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() != null) {
                hex.getBuilding().decrementFloodHalt();
            }
        }

        // ── Step 2: purge dead units ──────────────────────────────────────────
        gameMap.removeDeadUnits();

        // ── Step 3: natural disasters — bear AI + disaster events ─────────────
        DisasterController disasterController = new DisasterController(gameMap);
        disasterController.processBearAI();
        disasterController.checkAndTriggerDisasters();

        // ── Step 4: tribe AI ──────────────────────────────────────────────────
        tribeController.processTribesTurn();

        // ── Step 5 + 6: per-player economy and production queue (B33) ─────────
        List<TownHall> playerTownHalls = gameMap.getAllPlayerTownHalls();

        if (playerTownHalls.isEmpty()) {
            // ── Single-player mode: process the default single TownHall ────────
            boolean starving = economyController.processEndTurn(gameMap);
            gameMap.setStarving(starving);
            gameMap.getTownHall().advanceProductionQueue(starving);

        } else {
            // ── Multiplayer mode: process each player's TownHall separately ────
            boolean anyStarving = false;

            for (TownHall playerTH : playerTownHalls) {
                // Temporarily point map.getTownHall() to this player's TownHall
                gameMap.setActiveTownHall(playerTH);

                // Process economy for this player: production, upkeep, food consumption
                boolean playerStarving = economyController.processEndTurn(gameMap);

                // Advance this player's production queue
                playerTH.advanceProductionQueue(playerStarving);

                // Track starvation for AP penalty application below
                // (we use the starvation state of the TownHall whose units we'll process)
                if (playerStarving) anyStarving = true;
            }

            // Restore default TownHall — map.getTownHall() goes back to normal
            gameMap.clearActiveTownHall();
            gameMap.setStarving(anyStarving);
        }

        // ── Step 7: Apothecary crafting queues + item delivery (B11) ─────────
        for (Hex hex : gameMap.getHexes()) {
            if (!(hex.getBuilding() instanceof Apothecary apothecary)) continue;
            if (apothecary.isDestroyed()) continue;

            String completedItem = apothecary.advanceCraftingQueue();
            if (completedItem == null) continue;

            String ownerId = apothecary.getOwnerId();
            if (ownerId == null) continue;

            // Deliver the completed item to the owning player's inventory
            TownHall ownerTH = gameMap.getPlayerTownHall(ownerId);
            if (ownerTH != null) {
                ownerTH.getInventory().addItem(completedItem, 1);
                GameEventDispatcher.fireNotification("⚗️ Apothecary finished crafting: "
                        + completedItem + "! Added to your inventory.");
            }
        }

        // ── Step 8: reset AP + happiness/starvation penalties ─────────────────
        int effectiveHappiness = economyController.getEffectiveHappiness(gameMap);
        boolean isStarving     = gameMap.isStarving();

        for (Unit unit : gameMap.getUnits()) {
            if (!unit.isAlive()) continue;

            if (unit.getType() == UnitType.BEAR) {
                unit.resetAP();
                continue;
            }

            if (!unit.isEnemy()) {
                unit.resetAP();
                unit.resetItemBuffs(); // clear teleport/mobility/combat item buffs

                // Happiness ≤ −5: civilians and military lose 1 AP
                if (effectiveHappiness <= -5) {
                    UnitType t = unit.getType();
                    if (t == UnitType.WORKER    || t == UnitType.SWORDSMAN
                            || t == UnitType.ARCHER || t == UnitType.CAVALRY
                            || t == UnitType.CATAPULT) {
                        unit.consumeAP(1);
                    }
                }

                // Starvation: every non-NPC unit loses 1 AP
                if (isStarving) {
                    unit.consumeAP(1);
                }
            }
        }

        // ── Step 9: remove units that died from starvation / events ───────────
        gameMap.removeDeadUnits();
    }
}