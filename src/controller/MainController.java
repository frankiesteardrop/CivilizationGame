package controller;

import model.*;
import network.client.NetworkManager;
import java.util.List;

/**
 * Central client-side controller.
 *
 * <p>Two modes:
 * <ul>
 *   <li>Single-player: {@code networkManager} is null; all actions execute locally.</li>
 *   <li>Multiplayer: {@code networkManager} is non-null; UI actions send requests to
 *       the server; {@code myPlayerId} and {@code myTurn} control what the player can do.</li>
 * </ul>
 */
public class MainController {

    private final GameMap            gameMap;
    private final TurnController     turnController;
    private final UnitController     unitController;
    private final BuildController    buildController;
    private final UpgradeController  upgradeController;
    private final EconomyController  economyController;
    private final TradeController    tradeController;
    private final TribeController    tribeController;
    private final SaveLoadController saveLoadController;

    /** Non-null only in multiplayer mode. */
    private NetworkManager networkManager = null;

    /**
     * The ID the server assigned to this client. Used to determine ownership
     * of units/buildings in multiplayer context. Null in single-player. (B30)
     */
    private String myPlayerId = null;

    /**
     * Whether it is currently this client's turn. Always true in single-player.
     * Set to false by HUDPanel when server indicates another player's turn. (B31)
     */
    private boolean myTurn = true;

    private boolean processingTurn = false;

    // ─── Construction ─────────────────────────────────────────────────────────

    public MainController(GameMap gameMap) {
        this.gameMap            = gameMap;
        this.economyController  = new EconomyController(this);
        this.tradeController    = new TradeController(gameMap);
        this.tribeController    = new TribeController(gameMap);
        this.turnController     = new TurnController(this, gameMap);
        this.unitController     = new UnitController();
        this.buildController    = new BuildController(gameMap);
        this.upgradeController  = new UpgradeController(gameMap);
        this.saveLoadController = new SaveLoadController(this);

        boolean hasNoTribes = gameMap.getHexes().stream()
                .noneMatch(h -> h.getBuilding() instanceof TribeCamp);
        if (hasNoTribes) tribeController.spawnInitialTribes();
    }

    // ─── Multiplayer Mode ─────────────────────────────────────────────────────

    public void setNetworkManager(NetworkManager nm) { this.networkManager = nm; }
    public NetworkManager getNetworkManager()        { return networkManager; }

    /**
     * Sets the player's own ID (received from server after joining lobby). (B30)
     * Used by isAttackable() to distinguish own units from opponents'.
     */
    public void setMyPlayerId(String id) { this.myPlayerId = id; }
    public String getMyPlayerId()        { return myPlayerId; }

    /**
     * Updates whether it is currently this client's turn. (B31)
     * Called by HUDPanel.setActiveTurnInfo() when a GAME_STATE_UPDATE arrives.
     */
    public void setMyTurn(boolean myTurn) { this.myTurn = myTurn; }

    /**
     * Returns true if this client can take actions right now.
     * Always true in single-player. In multiplayer, false while waiting
     * for other players to take their turns. (B31)
     */
    public boolean isMyTurn() { return myTurn; }

    // ─── Getters ──────────────────────────────────────────────────────────────

    public GameMap            getGameMap()             { return gameMap; }
    public TurnController     getTurnController()      { return turnController; }
    public UnitController     getUnitController()      { return unitController; }
    public BuildController    getBuildController()     { return buildController; }
    public UpgradeController  getUpgradeController()   { return upgradeController; }
    public EconomyController  getEconomyController()   { return economyController; }
    public TradeController    getTradeController()     { return tradeController; }
    public TribeController    getTribeController()     { return tribeController; }
    public SaveLoadController getSaveLoadController()  { return saveLoadController; }

    public boolean isProcessingTurn()             { return processingTurn; }
    public void    setProcessingTurn(boolean val) { this.processingTurn = val; }

    // ─── Delegated Unit Actions ───────────────────────────────────────────────

    public Unit    selectUnitAt(Hex hex)                 { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex)     { return unitController.canMove(unit, targetHex, gameMap); }
    public void    executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    // ─── Hostility / Attackability (B30) ─────────────────────────────────────

    /**
     * Returns true if the given hex contains a hostile target.
     *
     * <p>Single-player: checks {@code unit.isEnemy()} (NPC flag) and hostile tribe status.
     * Multiplayer (B30): checks whether any unit's {@code ownerId} differs from ours,
     * because in multiplayer "hostile" is determined by diplomatic status on the server,
     * not the client-side {@code isEnemy()} flag which is reserved for NPCs.
     */
    public boolean isHostile(Hex hex) {
        if (hex == null) return false;

        boolean hasAnimal = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                        && u.getType() == UnitType.BEAR);

        if (networkManager != null && myPlayerId != null) {
            // ── Multiplayer (B30): any unit or building not owned by us is potentially hostile ──
            boolean hasOpponentUnit = gameMap.getUnits().stream()
                    .anyMatch(u -> u.isAlive()
                            && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                            && !myPlayerId.equals(u.getOwnerId())
                            && u.getType() != UnitType.BEAR); // bears checked separately
            boolean hasOpponentBuilding = hex.getBuilding() != null
                    && !hex.getBuilding().isDestroyed()
                    && !myPlayerId.equals(hex.getBuilding().getOwnerId());
            return hasAnimal || hasOpponentUnit || hasOpponentBuilding;
        }

        // ── Single-player: use NPC enemy flag and tribe hostile state ──────────
        boolean hasEnemyUnit  = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                        && u.isEnemy());
        boolean hasTribeEnemy = (hex.getBuilding() instanceof TribeCamp camp)
                && camp.getTribe().getState().isHostile();
        return hasAnimal || hasEnemyUnit || hasTribeEnemy;
    }

    /**
     * Returns true if the player can initiate an attack on this hex.
     * In multiplayer, the server enforces diplomatic status; here we just
     * pre-check to decide whether to show the attack menu. (B30)
     */
    public boolean isAttackable(Hex hex) {
        if (hex == null) return false;
        if (isHostile(hex)) return true;
        // Tribe camps are always attackable even when not yet "Enemy"
        if (hex.getBuilding() instanceof TribeCamp && !hex.getBuilding().isDestroyed()) return true;
        return false;
    }

    public boolean isCapturable(Unit unit, Hex hex) {
        if (unit == null || hex == null) return false;
        if (hex.getBuilding() instanceof TribeCamp) return false;
        int dist = gameMap.getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR());
        if (unit.getAttackRange() <= 0 || isHostile(hex) || hex.isInsideBorder() || dist != 1) return false;
        for (int i = 0; i < 6; i++) {
            Hex neighbor = gameMap.getNeighbor(hex, i);
            if (neighbor != null && neighbor.getBuilding() instanceof TribeCamp camp) {
                if (camp.getTribe().getState().getName().equals("Enemy")) return true;
            }
        }
        return false;
    }

    // ─── Menu Actions ─────────────────────────────────────────────────────────

    public List<MenuAction> getTownHallMenuActions() {
        return view.ContextMenuFactory.buildTownHallMenu(this);
    }

    public List<MenuAction> getStableMenuActions() {
        return view.ContextMenuFactory.buildStableMenu(this);
    }

    public List<MenuAction> getBazaarMenuActions(Bazaar bazaar, Runnable onTradeAction) {
        return view.ContextMenuFactory.buildBazaarMenu(this, bazaar, onTradeAction);
    }

    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex targetHex) {
        return view.ContextMenuFactory.buildUnitMenu(this, selectedUnit, targetHex);
    }
}