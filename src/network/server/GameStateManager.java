package network.server;

import com.google.gson.Gson;
import controller.CombatController;
import model.*;
import model.maps.MapDefinition;
import model.maps.PreDesignedMaps;
import network.messages.game.AttackRequest;
import network.messages.game.DiplomacyRequest;
import network.messages.game.ErrorResponse;
import network.messages.game.GameStartBroadcast;
import network.messages.game.GameStateBroadcast;
import network.messages.game.ItemUseRequest;
import network.messages.game.TradeOfferRequest;
import network.messages.game.TradeResponseRequest;
import network.messages.lobby.LobbyPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authority over the game state (Single Source of Truth).
 *
 * <p>All public methods are {@code synchronized} to prevent race conditions
 * when multiple client threads call them concurrently.
 */
public class GameStateManager {

    private final GameServer server;
    private final Gson gson;

    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    /** Diplomatic state: outer = attacker, inner = defender, value = "Neutral"|"Enemy"|"Allied" */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates =
            new ConcurrentHashMap<>();

    /** Handles all end-of-turn game logic (economy, disasters, tribes, AP resets). */
    private ServerTurnProcessor serverTurnProcessor;

    /**
     * The ID of the pre-designed map to load when the game starts.
     * Set by the constructor using the host's lobby selection.
     */
    private final String selectedMapId;

    /** Fog-of-War filter — reused across turns for efficiency. */
    private FogOfWarFilter fogOfWarFilter;

    // ─── Construction ─────────────────────────────────────────────────────────

    public GameStateManager(GameServer server,
                            ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers,
                            String selectedMapId) {
        this.server             = server;
        this.gson               = new Gson();
        this.players            = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
        this.selectedMapId      = selectedMapId;
    }

    // ─── Game Initialization (B5 + B10) ──────────────────────────────────────

    /**
     * Initializes the game using the host-selected pre-designed map.
     * <ol>
     *   <li>Loads the {@link MapDefinition} from {@link PreDesignedMaps}.</li>
     *   <li>Creates a reproducible {@link GameMap} with the map's fixed seed.</li>
     *   <li>Clears the default single-player setup (TH at 0,0).</li>
     *   <li>Places each player's Town Hall and initial units at their spawn point.</li>
     *   <li>Broadcasts {@link GameStartBroadcast} so clients switch to game view.</li>
     *   <li>Sends the initial Fog-of-War-filtered state to each client.</li>
     * </ol>
     */
    public synchronized void initializeGame() {
        // ── Load pre-designed map definition ──────────────────────────────────
        MapDefinition mapDef = PreDesignedMaps.getMap(selectedMapId);
        System.out.println("[Server] Loading map: " + mapDef.getDisplayName()
                + " | seed=" + mapDef.getRandomSeed());

        // ── Create reproducible map (B10) ─────────────────────────────────────
        this.masterMap = new GameMap(mapDef.getRadius(), mapDef.getRandomSeed());

        // Remove the default single-player Town Hall and units at (0,0)
        masterMap.clearCenterSetup();

        // ── Assign player spawns (B10) ────────────────────────────────────────
        List<int[]> spawnPoints = mapDef.getSpawnPoints();
        for (int i = 0; i < players.size(); i++) {
            if (i >= spawnPoints.size()) {
                System.err.println("[Server] Warning: more players than spawn points on this map!");
                break;
            }
            String playerId = players.get(i).getId();
            int[] spawn = spawnPoints.get(i);
            masterMap.placePlayerSpawn(playerId, spawn[0], spawn[1]);
            System.out.println("[Server] Player " + players.get(i).getUsername()
                    + " spawned at (" + spawn[0] + ", " + spawn[1] + ")");
        }

        // ── Initialize Fog-of-War filter (B3) ────────────────────────────────
        this.fogOfWarFilter = new FogOfWarFilter(gson);

        // ── Create server turn processor ──────────────────────────────────────
        this.serverTurnProcessor = new ServerTurnProcessor(masterMap);

        // ── Initialize diplomatic relationships ───────────────────────────────
        for (LobbyPlayer p1 : players) {
            ConcurrentHashMap<String, String> relations = new ConcurrentHashMap<>();
            for (LobbyPlayer p2 : players) {
                if (!p1.getId().equals(p2.getId())) {
                    relations.put(p2.getId(), "Neutral");
                }
            }
            diplomacyStates.put(p1.getId(), relations);
        }

        System.out.println("✅ [Server] Game initialized with " + players.size() + " players.");

        // ── Signal all clients to switch from LobbyPanel to GamePanel (B5) ────
        server.broadcast(gson.toJson(new GameStartBroadcast()));

        // ── Send initial Fog-of-War-filtered state to each client ─────────────
        broadcastCustomizedStates();
    }

    // ─── Turn Management ─────────────────────────────────────────────────────

    public synchronized void handleEndTurn(String clientId) {
        String activeId = players.get(currentPlayerIndex).getId();

        if (!activeId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("It is not your turn!")));
            return;
        }

        System.out.println("[Server] Processing end-of-turn for player: " + clientId);

        serverTurnProcessor.processTurn(masterMap);

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        if (currentPlayerIndex == 0) {
            masterMap.incrementTurn();
            System.out.println("[Server] Round complete. Global turn: "
                    + masterMap.getCurrentTurn());
        }

        System.out.println("[Server] Next turn: " + players.get(currentPlayerIndex).getUsername());
        broadcastCustomizedStates();
    }

    // ─── Item Use ─────────────────────────────────────────────────────────────

    public synchronized void handleItemUseRequest(String clientId, ItemUseRequest req) {
        if (!players.get(currentPlayerIndex).getId().equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Unit target = masterMap.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == req.getTargetQ() && u.getR() == req.getTargetR())
                .findFirst().orElse(null);

        if (target == null || !clientId.equals(target.getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("You can only use items on your own units!")));
            return;
        }

        if (target.hasUsedItemThisTurn()) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("This unit has already used an item this turn!")));
            return;
        }

        Inventory playerInv = getPlayerInventory(clientId);
        if (playerInv == null || !playerInv.consumeItem(req.getItemName())) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("You do not have this item in your inventory!")));
            return;
        }

        target.setUsedItemThisTurn(true);

        switch (req.getItemName()) {
            case "TELEPORT" -> {
                Hex dest = masterMap.getHexAt(req.getDestQ(), req.getDestR());
                if (dest != null
                        && !masterMap.hasUnitAt(dest.getQ(), dest.getR())
                        && dest.isExplored()
                        && dest.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                        && dest.getTerrainType() != TerrainType.SEA) {
                    target.moveTo(dest.getQ(), dest.getR(), 0);
                } else {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("Invalid teleport destination!")));
                    return;
                }
            }
            case "MOBILITY" -> target.addTemporaryAP(2);
            case "COMBAT"   -> {
                target.setTemporaryCombatDiceBonus(1);
                target.setTemporarySiegeBonus(5);
            }
        }
        broadcastCustomizedStates();
    }

    // ─── Attack ───────────────────────────────────────────────────────────────

    public synchronized void handleAttackRequest(String clientId, AttackRequest req) {
        if (!players.get(currentPlayerIndex).getId().equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Hex sourceHex = masterMap.getHexAt(req.getSourceQ(), req.getSourceR());
        Hex targetHex = masterMap.getHexAt(req.getTargetQ(), req.getTargetR());

        if (sourceHex == null || targetHex == null) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Invalid hex coordinates.")));
            return;
        }

        List<Unit> attackers = new ArrayList<>();
        for (Unit u : masterMap.getUnits()) {
            if (u.isAlive() && u.getQ() == sourceHex.getQ() && u.getR() == sourceHex.getR()) {
                if (!clientId.equals(u.getOwnerId())) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("You do not own these units!")));
                    return;
                }
                attackers.add(u);
            }
        }

        if (attackers.isEmpty()) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("No valid attacking units found.")));
            return;
        }

        String targetOwnerId = getTargetOwnerId(targetHex);

        if (targetOwnerId != null && targetOwnerId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("You cannot attack your own units or structures!")));
            return;
        }

        if (targetOwnerId != null) {
            String diploStatus = getDiplomaticStatus(clientId, targetOwnerId);
            if (!"Enemy".equals(diploStatus)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse(
                        "You must declare war first! Current status: " + diploStatus)));
                return;
            }
        }

        boolean isTargetAnimal = masterMap.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getType() == UnitType.BEAR
                        && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR());

        boolean hasEnemyUnit = masterMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                        && !clientId.equals(u.getOwnerId()));

        boolean isSiegeAttack = !hasEnemyUnit && !isTargetAnimal;

        boolean targetHasWall = false;
        for (int i = 0; i < 6; i++) {
            if (masterMap.getNeighbor(sourceHex, i) == targetHex) {
                targetHasWall = sourceHex.hasWall(i);
                break;
            }
        }

        CombatController cc = new CombatController(masterMap);
        int result = cc.executeAttack(attackers, sourceHex, targetHex,
                isSiegeAttack, isTargetAnimal, targetHasWall);

        if (result == -1) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Attack failed. Check AP or attack range.")));
            return;
        }

        if (targetOwnerId != null && isSiegeAttack) {
            checkPlayerElimination(targetOwnerId);
        }

        broadcastCustomizedStates();
    }

    // ─── Diplomacy Stub (B13) ─────────────────────────────────────────────────

    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        // TODO (B13): implement war declaration, alliance request, alliance break
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Diplomacy system will be implemented in step B13.")));
    }

    // ─── Trade Stubs (B12) ────────────────────────────────────────────────────

    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Trade inbox will be implemented in step B12.")));
    }

    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Trade response will be implemented in step B12.")));
    }

    // ─── Player Elimination ───────────────────────────────────────────────────

    private void checkPlayerElimination(String playerId) {
        boolean hasActiveTH = false;
        for (Hex h : masterMap.getHexes()) {
            if (h.getBuilding() != null
                    && h.getBuilding().getType() == BuildingType.TOWN_HALL
                    && !h.getBuilding().isDestroyed()
                    && playerId.equals(h.getBuilding().getOwnerId())) {
                hasActiveTH = true;
                break;
            }
        }

        if (!hasActiveTH) {
            // Kill all units using takeDamage to avoid unmodifiable-list crash (B4/B32 fix)
            masterMap.getUnits().stream()
                    .filter(u -> playerId.equals(u.getOwnerId()))
                    .forEach(u -> u.takeDamage(u.getMaxHp() + 1));
            masterMap.removeDeadUnits();

            for (Hex h : masterMap.getHexes()) {
                if (h.getBuilding() != null && playerId.equals(h.getBuilding().getOwnerId())) {
                    h.getBuilding().takeDamage(9999);
                    h.setBuilding(null);
                }
            }
            server.broadcast(gson.toJson(
                    new ErrorResponse("💀 Player " + playerId + " has been eliminated!")));
        }
    }

    // ─── Broadcast (B3 — Fog of War per player) ───────────────────────────────

    /**
     * Sends a customized, Fog-of-War-filtered game state to each connected client.
     * Each player only receives data about hexes within their vision radius.
     */
    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();

            // B3: apply Fog-of-War filter — each client gets only what they can see
            String filteredMapJson = fogOfWarFilter.filterForPlayer(masterMap, clientId);

            GameStateBroadcast update = new GameStateBroadcast(
                    activePlayerId, masterMap.getCurrentTurn(), filteredMapJson);
            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private Inventory getPlayerInventory(String ownerId) {
        for (Hex h : masterMap.getHexes()) {
            if (h.getBuilding() != null
                    && h.getBuilding().getType() == BuildingType.TOWN_HALL
                    && ownerId.equals(h.getBuilding().getOwnerId())) {
                return ((TownHall) h.getBuilding()).getInventory();
            }
        }
        return masterMap.getTownHall().getInventory();
    }

    private String getTargetOwnerId(Hex targetHex) {
        for (Unit u : masterMap.getUnits()) {
            if (u.isAlive()
                    && u.getQ() == targetHex.getQ()
                    && u.getR() == targetHex.getR()
                    && u.getOwnerId() != null) {
                return u.getOwnerId();
            }
        }
        if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
            return targetHex.getBuilding().getOwnerId();
        }
        return null;
    }

    private String getDiplomaticStatus(String attackerId, String defenderId) {
        if (attackerId == null || defenderId == null) return "Neutral";
        ConcurrentHashMap<String, String> relations = diplomacyStates.get(attackerId);
        if (relations == null) return "Neutral";
        return relations.getOrDefault(defenderId, "Neutral");
    }
}