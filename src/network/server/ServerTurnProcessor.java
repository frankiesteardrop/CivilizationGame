package network.server;

import controller.DisasterController;
import controller.EconomyController;
import controller.TribeController;
import model.*;

/**
 * Handles all server-side end-of-turn game logic for multiplayer games.
 *
 * <p>This is the server equivalent of {@code TurnController.executeEndTurnLogic()},
 * but designed to operate without a {@code MainController} or UI dependencies.
 * One instance is created per game session and reused across turns.
 *
 * <p>Responsibilities (in order of execution):
 * <ol>
 *   <li>Decrement flood-halt timers on buildings.</li>
 *   <li>Remove dead units from the map.</li>
 *   <li>Run bear AI and natural disaster checks.</li>
 *   <li>Run tribe AI turn behavior.</li>
 *   <li>Process economy: resource production, upkeep, food consumption.</li>
 *   <li>Advance production queues for all active Town Halls.</li>
 *   <li>Reset AP for all living units; apply happiness/starvation penalties.</li>
 *   <li>Clear per-turn item buffs on all units.</li>
 * </ol>
 */
public class ServerTurnProcessor {

    /**
     * Economy controller instance — kept alive across turns so it can
     * track happiness-state deltas (monument changes, settlement changes, etc.)
     * Uses the no-arg server constructor that does not require a MainController.
     */
    private final EconomyController economyController;

    /**
     * Tribe controller — registered as a UnitListener so it can track
     * mission progress when units are killed during combat.
     */
    private final TribeController tribeController;

    /**
     * Constructs a new processor for the given game map.
     * Must be called once after {@code GameMap} has been initialized.
     *
     * @param gameMap the authoritative server-side game state
     */
    public ServerTurnProcessor(GameMap gameMap) {
        this.economyController = new EconomyController();       // no-arg server constructor
        this.tribeController   = new TribeController(gameMap);  // registers as UnitListener
    }

    /**
     * Executes all end-of-turn processing on the master map.
     * Called by {@link GameStateManager} each time a player ends their turn.
     *
     * @param gameMap the authoritative game state to mutate in place
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

        // ── Step 4: tribe AI behavior for this turn ───────────────────────────
        tribeController.processTribesTurn();

        // ── Step 5: economy ───────────────────────────────────────────────────
        // NOTE: EconomyController.processEndTurn() is designed for single-player
        // (it uses map.getTownHall() for the single inventory). Full per-player
        // economy requires a redesigned GameMap with per-player TownHall lookup.
        // For multiplayer the base economy still runs; per-player production queues
        // are advanced separately in Step 6.
        boolean isStarving = economyController.processEndTurn(gameMap);
        gameMap.setStarving(isStarving);

        // ── Step 6: advance production queues for ALL active Town Halls ────────
        // processEndTurn() already advances the "default" TownHall (at 0,0).
        // We additionally advance every player-owned TownHall (ownerId != null).
        for (Hex hex : gameMap.getHexes()) {
            if (hex.getBuilding() instanceof TownHall th
                    && !th.isDestroyed()
                    && th.getOwnerId() != null) {
                th.advanceProductionQueue(isStarving);
            }
        }

        // ── Step 7: reset unit AP + apply happiness / starvation penalties ─────
        int effectiveHappiness = economyController.getEffectiveHappiness(gameMap);

        for (Unit unit : gameMap.getUnits()) {
            if (!unit.isAlive()) continue;

            if (unit.getType() == UnitType.BEAR) {
                // Bears only get their AP reset — no other modifiers apply
                unit.resetAP();
                continue;
            }

            if (!unit.isEnemy()) {
                unit.resetAP();
                unit.resetItemBuffs();     // clear teleport/mobility/combat item buffs

                // Happiness ≤ −5: certain unit types lose 1 AP
                if (effectiveHappiness <= -5) {
                    UnitType t = unit.getType();
                    if (t == UnitType.WORKER    || t == UnitType.SWORDSMAN
                            || t == UnitType.ARCHER || t == UnitType.CAVALRY) {
                        unit.consumeAP(1);
                    }
                }

                // Starvation: every civilian and military unit loses 1 AP
                if (isStarving) {
                    unit.consumeAP(1);
                }
            }
        }

        // ── Step 8: remove any units that may have died during starvation ──────
        gameMap.removeDeadUnits();
    }
}