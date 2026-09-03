package network.server;

import com.google.gson.Gson;
import controller.CombatController;
import model.*;
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
 * Server-side authority over the game state.
 * All game logic is executed here; clients only send requests and render results.
 *
 * <p>This class is the Single Source of Truth for:
 * <ul>
 *   <li>The master {@link GameMap} (with all unit, building, and resource data).</li>
 *   <li>Whose turn it currently is.</li>
 *   <li>Diplomatic relationships between players.</li>
 * </ul>
 *
 * <p>All public methods are {@code synchronized} to prevent race conditions
 * when multiple client threads call them concurrently.
 */
public class GameStateManager {

    private final GameServer server;
    private final Gson gson;

    /** The authoritative game map — never sent raw to clients; always Fog-of-War filtered. */
    private GameMap masterMap;

    /** Ordered list of players (determines turn order). */
    private final List<LobbyPlayer> players;

    /** Index into {@code players} for the currently active player. */
    private int currentPlayerIndex;

    /**
     * Diplomatic state matrix: outer key = player A, inner key = player B,
     * value = "Neutral" | "Enemy" | "Allied".
     */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates =
            new ConcurrentHashMap<>();

    /**
     * Handles all end-of-turn game logic (economy, disasters, tribes, AP resets).
     * Initialized in {@link #initializeGame()}.
     */
    private ServerTurnProcessor serverTurnProcessor;

    // ─── Construction ─────────────────────────────────────────────────────────

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers) {
        this.server             = server;
        this.gson               = new Gson();
        this.players            = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
    }

    // ─── Game Initialization (B5) ─────────────────────────────────────────────

    /**
     * Initializes the game map, sets up diplomacy, creates the turn processor,
     * then broadcasts {@link GameStartBroadcast} to all clients so they switch
     * from the LobbyPanel to the GamePanel, followed by the initial game state.
     */
    public synchronized void initializeGame() {
        // Create the authoritative game map
        this.masterMap = new GameMap(20);

        // Create the server-side turn processor (economy, disasters, tribes, AP resets)
        this.serverTurnProcessor = new ServerTurnProcessor(masterMap);

        // Initialize diplomatic relationships: everyone starts Neutral
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

        // ── B5: signal all clients to switch from LobbyPanel to GamePanel ──────
        server.broadcast(gson.toJson(new GameStartBroadcast()));

        // Send the initial game state to each client (with Fog of War per player)
        broadcastCustomizedStates();
    }

    // ─── Turn Management (B7) ─────────────────────────────────────────────────

    /**
     * Processes an "End Turn" request from a client.
     *
     * <p>Validates that it is indeed this client's turn, then:
     * <ol>
     *   <li>Runs all end-of-turn game logic via {@link ServerTurnProcessor}.</li>
     *   <li>Advances the player index to the next player.</li>
     *   <li>Increments the global turn counter when a full round completes.</li>
     *   <li>Broadcasts the updated, filtered game state to all clients.</li>
     * </ol>
     */
    public synchronized void handleEndTurn(String clientId) {
        String activeId = players.get(currentPlayerIndex).getId();

        if (!activeId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("It is not your turn!")));
            return;
        }

        System.out.println("[Server] Processing end-of-turn for player: " + clientId);

        // ── B7: run full turn logic (economy, disasters, tribes, AP resets) ────
        serverTurnProcessor.processTurn(masterMap);

        // Advance to the next player
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        // When all players have had a turn, increment the global turn counter
        if (currentPlayerIndex == 0) {
            masterMap.incrementTurn();
            System.out.println("[Server] Round complete. Global turn is now: "
                    + masterMap.getCurrentTurn());
        }

        String nextPlayerId = players.get(currentPlayerIndex).getId();
        System.out.println("[Server] Next player's turn: " + nextPlayerId);

        // Broadcast updated, Fog-of-War-filtered state to every client
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
                // Validate: destination must exist, be unoccupied, explored (no FoW),
                // and passable terrain for this unit
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
                        "You must declare war first! Current diplomatic status: " + diploStatus)));
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
        // TODO (B12): validate resources, lock them, deliver to target's inbox
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Trade inbox will be implemented in step B12.")));
    }

    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        // TODO (B12): accept / reject pending trade offer
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
            // Kill all units belonging to this player using takeDamage
            // (avoids UnsupportedOperationException on unmodifiable list — B4 fix)
            masterMap.getUnits().stream()
                    .filter(u -> playerId.equals(u.getOwnerId()))
                    .forEach(u -> u.takeDamage(u.getMaxHp() + 1));
            masterMap.removeDeadUnits();

            // Raze all buildings belonging to this player
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

    // ─── Broadcast ────────────────────────────────────────────────────────────

    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            GameMap playerSpecificMap = filterMapForPlayer(masterMap, clientId);

            String mapJson = gson.toJson(playerSpecificMap);
            GameStateBroadcast update = new GameStateBroadcast(
                    activePlayerId, masterMap.getCurrentTurn(), mapJson);
            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Returns a version of the map filtered by Fog of War for the given player.
     * TODO (B3): implement proper per-player FoW filtering.
     */
    private GameMap filterMapForPlayer(GameMap master, String playerId) {
        // TODO (B3): return only hexes visible within this player's vision radius
        return master;
    }

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