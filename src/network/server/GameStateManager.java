package network.server;

import com.google.gson.Gson;
import controller.CombatController;
import model.*;
import model.maps.MapDefinition;
import model.maps.PreDesignedMaps;
import network.messages.game.*;
import network.messages.lobby.LobbyPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side authority over the game state (Single Source of Truth).
 *
 * <p>All public methods are {@code synchronized} to prevent race conditions.
 */
public class GameStateManager {

    private final GameServer server;
    private final Gson gson;

    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    /** Diplomatic status matrix: outer = player, inner = other player, value = status string. */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates =
            new ConcurrentHashMap<>();

    /**
     * Pending alliance requests: key = requesterId, value = targetId.
     * Removed when accepted, rejected, or superseded by a war declaration.
     */
    private final ConcurrentHashMap<String, String> pendingAllianceRequests = new ConcurrentHashMap<>();

    /**
     * Trade inboxes: key = target playerId, value = list of pending offers.
     * Resource locking is applied in the offerer's Inventory.
     */
    private final ConcurrentHashMap<String, List<TradeOffer>> tradeInboxes = new ConcurrentHashMap<>();

    /**
     * Pending war reports per player: key = targetPlayerId, value = list of reports.
     * Cleared when delivered at the start of that player's turn. (B14)
     */
    private final ConcurrentHashMap<String, List<WatReport>> pendingWatReports = new ConcurrentHashMap<>();

    /** playerId → display username (populated at game start). */
    private final Map<String, String> playerNames = new HashMap<>();

    private ServerTurnProcessor serverTurnProcessor;
    private final String selectedMapId;
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

    // ─── Initialization ───────────────────────────────────────────────────────

    public synchronized void initializeGame() {
        MapDefinition mapDef = PreDesignedMaps.getMap(selectedMapId);
        System.out.println("[Server] Loading map: " + mapDef.getDisplayName());

        this.masterMap = new GameMap(mapDef.getRadius(), mapDef.getRandomSeed());
        masterMap.clearCenterSetup();

        List<int[]> spawnPoints = mapDef.getSpawnPoints();
        for (int i = 0; i < players.size(); i++) {
            if (i >= spawnPoints.size()) break;
            String playerId = players.get(i).getId();
            int[] spawn = spawnPoints.get(i);
            masterMap.placePlayerSpawn(playerId, spawn[0], spawn[1]);

            // Initialize player-specific structures
            playerNames.put(playerId, players.get(i).getUsername());
            tradeInboxes.put(playerId, new ArrayList<>());
            pendingWatReports.put(playerId, new ArrayList<>());

            System.out.println("[Server] Player " + players.get(i).getUsername()
                    + " spawned at (" + spawn[0] + "," + spawn[1] + ")");
        }

        // Diplomacy: all players start Neutral toward each other
        for (LobbyPlayer p1 : players) {
            ConcurrentHashMap<String, String> relations = new ConcurrentHashMap<>();
            for (LobbyPlayer p2 : players) {
                if (!p1.getId().equals(p2.getId())) {
                    relations.put(p2.getId(), "Neutral");
                }
            }
            diplomacyStates.put(p1.getId(), relations);
        }

        this.fogOfWarFilter    = new FogOfWarFilter(gson);
        this.serverTurnProcessor = new ServerTurnProcessor(masterMap);

        System.out.println("✅ [Server] Game initialized with " + players.size() + " players.");

        server.broadcast(gson.toJson(new GameStartBroadcast()));
        broadcastCustomizedStates();
    }

    // ─── Turn Management ─────────────────────────────────────────────────────

    public synchronized void handleEndTurn(String clientId) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        System.out.println("[Server] End-of-turn for: " + playerNames.getOrDefault(clientId, clientId));
        serverTurnProcessor.processTurn(masterMap);

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        if (currentPlayerIndex == 0) {
            masterMap.incrementTurn();
        }

        // B14: deliver pending war reports to the player whose turn is now starting
        String nextPlayerId = players.get(currentPlayerIndex).getId();
        deliverWatReports(nextPlayerId);

        broadcastCustomizedStates();
    }

    // ─── B18: Player Disconnected ──────────────────────────────────────────────

    /**
     * Called when a client TCP connection drops during an active game. (B18)
     *
     * <p>Broadcasts a disconnect notification to all remaining players.
     * If it was the disconnected player's turn, automatically processes their
     * end-of-turn so the game does not freeze.
     */
    public synchronized void handlePlayerDisconnected(String clientId) {
        if (serverTurnProcessor == null) return; // game not yet initialized

        String playerName = playerNames.getOrDefault(clientId, clientId);
        System.out.println("[Server] Player disconnected during game: " + playerName);

        // Cancel all pending trades for the disconnected player
        cancelAllTradesForPlayer(clientId);

        // Notify remaining players
        server.broadcast(gson.toJson(new GameNotificationMessage(
                "⚠️ Player " + playerName + " has disconnected.")));

        // Auto-skip if it was their turn (B18)
        if (players.get(currentPlayerIndex).getId().equals(clientId)) {
            server.broadcast(gson.toJson(new GameNotificationMessage(
                    "⏭️ " + playerName + "'s turn was automatically skipped.")));

            serverTurnProcessor.processTurn(masterMap);

            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (currentPlayerIndex == 0) {
                masterMap.incrementTurn();
            }

            // Deliver war reports to the new active player
            String nextPlayerId = players.get(currentPlayerIndex).getId();
            deliverWatReports(nextPlayerId);

            broadcastCustomizedStates();
        }
    }

    // ─── B11: Apothecary Item Crafting ────────────────────────────────────────

    /**
     * Handles a request from a player to craft an item at their Apothecary.
     *
     * <p>Validation:
     * <ol>
     *   <li>Must be the active player's turn.</li>
     *   <li>The hex must contain an Apothecary owned by this player.</li>
     *   <li>The Apothecary's crafting queue must be empty.</li>
     *   <li>The item name must be a valid {@link Apothecary.ItemType}.</li>
     *   <li>The player must have sufficient resources.</li>
     * </ol>
     */
    public synchronized void handleCraftItemRequest(String clientId, CraftItemRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Hex hex = masterMap.getHexAt(req.getApothecaryQ(), req.getApothecaryR());
        if (hex == null || !(hex.getBuilding() instanceof Apothecary apothecary)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("No Apothecary found at the specified location.")));
            return;
        }

        if (!clientId.equals(hex.getBuilding().getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("You do not own this Apothecary.")));
            return;
        }

        if (!apothecary.canQueueItem()) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("This Apothecary is already crafting: "
                            + apothecary.getCurrentlyCrafting())));
            return;
        }

        String itemName = req.getItemName();
        if (!Apothecary.ItemType.isValid(itemName)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Unknown item type: " + itemName)));
            return;
        }

        Apothecary.ItemType itemType;
        try {
            itemType = Apothecary.ItemType.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Invalid item name.")));
            return;
        }

        Inventory inv = getPlayerInventory(clientId);
        if (inv == null) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Cannot find player inventory.")));
            return;
        }

        // Validate resources
        if (!inv.hasEnough(ResourceType.FOOD,  itemType.getFoodCost())
                || !inv.hasEnough(ResourceType.STONE, itemType.getStoneCost())
                || !inv.hasEnough(ResourceType.IRON,  itemType.getIronCost())
                || !inv.hasEnough(ResourceType.WOOD,  itemType.getWoodCost())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse(
                    "Not enough resources to craft " + itemType.getDisplayName()
                            + ". Need: " + itemType.getFoodCost() + " Food, "
                            + itemType.getStoneCost() + " Stone, "
                            + itemType.getIronCost() + " Iron, "
                            + itemType.getWoodCost() + " Wood.")));
            return;
        }

        // Deduct resources and queue item
        inv.consumeResource(ResourceType.FOOD,  itemType.getFoodCost());
        inv.consumeResource(ResourceType.STONE, itemType.getStoneCost());
        inv.consumeResource(ResourceType.IRON,  itemType.getIronCost());
        inv.consumeResource(ResourceType.WOOD,  itemType.getWoodCost());
        apothecary.queueItem(itemName);

        System.out.println("[Server] Player " + playerNames.getOrDefault(clientId, clientId)
                + " crafting: " + itemName);

        server.sendToClient(clientId, gson.toJson(
                new GameNotificationMessage("⚗️ Crafting " + itemType.getDisplayName()
                        + " — will be ready at end of turn!")));
        broadcastCustomizedStates();
    }

    // ─── B12: Player-to-Player Trade ─────────────────────────────────────────

    /**
     * Handles a trade offer from Player A to Player B.
     *
     * <p>On success: Player A's offered resources are locked in their inventory
     * and the offer is placed in Player B's trade inbox.
     */
    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        // Validate target
        String targetId = req.getTargetPlayerId();
        if (targetId == null || targetId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Invalid trade target.")));
            return;
        }

        boolean targetExists = players.stream().anyMatch(p -> p.getId().equals(targetId));
        if (!targetExists) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Target player not found.")));
            return;
        }

        // Validate amounts
        if (req.getOfferAmount() <= 0 || req.getRequestAmount() <= 0) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Trade amounts must be greater than zero.")));
            return;
        }

        // Validate resources
        Inventory offererInv = getPlayerInventory(clientId);
        if (offererInv == null || !offererInv.hasEnough(req.getOfferType(), req.getOfferAmount())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse(
                    "You do not have enough " + req.getOfferType()
                            + " to offer! Need: " + req.getOfferAmount())));
            return;
        }

        // Lock offered resources so Player A cannot spend them while waiting
        offererInv.lockResource(req.getOfferType(), req.getOfferAmount());

        // Create and deliver the offer
        String offererName = playerNames.getOrDefault(clientId, clientId);
        TradeOffer offer = new TradeOffer(clientId, offererName, targetId,
                req.getOfferType(), req.getOfferAmount(),
                req.getRequestType(), req.getRequestAmount());

        tradeInboxes.computeIfAbsent(targetId, k -> new ArrayList<>()).add(offer);

        // Notify target
        sendTradeInboxUpdate(targetId);
        server.sendToClient(targetId, gson.toJson(new GameNotificationMessage(
                "📬 " + offererName + " has sent you a trade offer!")));

        // Confirm to sender
        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage(
                "✅ Trade offer sent to "
                        + playerNames.getOrDefault(targetId, targetId) + ".")));

        broadcastCustomizedStates();
    }

    /**
     * Handles Player B's acceptance or rejection of a trade offer,
     * and also handles Player A cancelling their own pending offer.
     */
    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        String tradeId = req.getTradeId();

        // Find the offer — it might be in this player's inbox (B responding)
        // or in someone else's inbox (A cancelling their own offer)
        TradeOffer offer = null;
        String inboxOwnerId = null;

        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            for (TradeOffer o : entry.getValue()) {
                if (o.getId().equals(tradeId)) {
                    offer = o;
                    inboxOwnerId = entry.getKey();
                    break;
                }
            }
            if (offer != null) break;
        }

        if (offer == null) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Trade offer not found. It may have been cancelled.")));
            return;
        }

        boolean isTarget  = clientId.equals(offer.getTargetId());
        boolean isOfferer = clientId.equals(offer.getOffererId());

        if (!isTarget && !isOfferer) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("You are not a party to this trade offer.")));
            return;
        }

        // Offerer is cancelling their own offer
        if (isOfferer && !req.isAccepted()) {
            removeTradeOffer(offer, inboxOwnerId);
            // Unlock A's resources
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            if (offererInv != null) {
                offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
            }
            server.sendToClient(clientId, gson.toJson(
                    new GameNotificationMessage("🚫 Trade offer cancelled. Resources returned.")));
            sendTradeInboxUpdate(offer.getTargetId());
            broadcastCustomizedStates();
            return;
        }

        if (!isTarget) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Only the target player can accept or reject this offer.")));
            return;
        }

        if (!req.isAccepted()) {
            // Target rejects the offer
            removeTradeOffer(offer, inboxOwnerId);
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            if (offererInv != null) {
                offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
            }
            server.sendToClient(offer.getOffererId(), gson.toJson(new GameNotificationMessage(
                    "❌ " + playerNames.getOrDefault(clientId, clientId)
                            + " rejected your trade offer. Resources returned.")));
            sendTradeInboxUpdate(clientId);
            broadcastCustomizedStates();
            return;
        }

        // Target accepts: validate B has the requested resources
        Inventory targetInv = getPlayerInventory(clientId);
        if (targetInv == null || !targetInv.hasEnough(offer.getRequestType(), offer.getRequestAmount())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse(
                    "You do not have enough " + offer.getRequestType()
                            + " to accept this trade! Need: " + offer.getRequestAmount())));
            return;
        }

        // Execute the trade
        // 1. Deduct B's resources
        targetInv.consumeResource(offer.getRequestType(), offer.getRequestAmount());
        // 2. Transfer A's locked resources to B
        Inventory offererInv = getPlayerInventory(offer.getOffererId());
        if (offererInv != null) {
            offererInv.consumeLockedResource(offer.getOfferType(), offer.getOfferAmount());
        }
        targetInv.addResource(offer.getOfferType(), offer.getOfferAmount());
        // 3. Give A their requested resources
        if (offererInv != null) {
            offererInv.addResource(offer.getRequestType(), offer.getRequestAmount());
        }

        removeTradeOffer(offer, inboxOwnerId);

        String summary = String.format("💱 Trade complete: %s gave %d %s and received %d %s.",
                playerNames.getOrDefault(offer.getOffererId(), "?"),
                offer.getOfferAmount(), offer.getOfferType(),
                offer.getRequestAmount(), offer.getRequestType());

        server.sendToClient(offer.getOffererId(), gson.toJson(new GameNotificationMessage(summary)));
        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage(summary)));

        sendTradeInboxUpdate(clientId);
        broadcastCustomizedStates();
    }

    // ─── B13: Diplomacy System ────────────────────────────────────────────────

    /**
     * Routes diplomacy actions: DECLARE_WAR, REQUEST_ALLIANCE, BREAK_ALLIANCE.
     */
    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        String targetId = req.getTargetPlayerId();
        if (!isValidTarget(clientId, targetId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Invalid diplomacy target.")));
            return;
        }

        switch (req.getAction()) {

            case "DECLARE_WAR" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if ("Enemy".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("You are already at war with this player.")));
                    return;
                }

                // War is mutual and unilateral
                setDiplomaticStatus(clientId, targetId, "Enemy");
                setDiplomaticStatus(targetId, clientId, "Enemy");

                // Cancel any pending alliance between them
                pendingAllianceRequests.remove(clientId);
                pendingAllianceRequests.entrySet().removeIf(e ->
                        e.getKey().equals(targetId) && e.getValue().equals(clientId));

                // Return any pending trade offers between these two players
                cancelTradesBetween(clientId, targetId);

                String msg = String.format("⚔️ %s has declared war on %s!",
                        playerNames.getOrDefault(clientId, clientId),
                        playerNames.getOrDefault(targetId, targetId));

                server.broadcast(gson.toJson(new DiplomacyBroadcast(
                        "WAR_DECLARED",
                        clientId, playerNames.getOrDefault(clientId, "?"),
                        targetId, playerNames.getOrDefault(targetId, "?"),
                        msg)));

                broadcastCustomizedStates();
            }

            case "REQUEST_ALLIANCE" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if ("Enemy".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("Cannot request alliance while at war. Declare peace first.")));
                    return;
                }
                if ("Allied".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("You are already allied with this player.")));
                    return;
                }
                // Check if there's already a pending request in either direction
                if (pendingAllianceRequests.containsKey(clientId)
                        && pendingAllianceRequests.get(clientId).equals(targetId)) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("You already have a pending alliance request to this player.")));
                    return;
                }

                pendingAllianceRequests.put(clientId, targetId);

                String requestMsg = String.format("🤝 %s has sent you an alliance request!",
                        playerNames.getOrDefault(clientId, clientId));
                server.sendToClient(targetId, gson.toJson(new DiplomacyBroadcast(
                        "ALLIANCE_REQUESTED",
                        clientId, playerNames.getOrDefault(clientId, "?"),
                        targetId, playerNames.getOrDefault(targetId, "?"),
                        requestMsg)));

                server.sendToClient(clientId, gson.toJson(new GameNotificationMessage(
                        "🤝 Alliance request sent to "
                                + playerNames.getOrDefault(targetId, targetId) + ".")));
            }

            case "BREAK_ALLIANCE" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if (!"Allied".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(
                            new ErrorResponse("You are not allied with this player.")));
                    return;
                }

                setDiplomaticStatus(clientId, targetId, "Neutral");
                setDiplomaticStatus(targetId, clientId, "Neutral");

                String msg = String.format("💔 %s has broken the alliance with %s!",
                        playerNames.getOrDefault(clientId, clientId),
                        playerNames.getOrDefault(targetId, targetId));

                server.broadcast(gson.toJson(new DiplomacyBroadcast(
                        "ALLIANCE_BROKEN",
                        clientId, playerNames.getOrDefault(clientId, "?"),
                        targetId, playerNames.getOrDefault(targetId, "?"),
                        msg)));

                broadcastCustomizedStates(); // FoW no longer shared after break
            }

            default -> server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("Unknown diplomacy action: " + req.getAction())));
        }
    }

    /**
     * Handles Player B's accept/reject response to a pending alliance request.
     */
    public synchronized void handleAllianceResponse(String clientId, AllianceResponseRequest req) {
        String requesterId = req.getRequesterId();

        // Verify there is actually a pending request from requesterId → clientId
        String pendingTarget = pendingAllianceRequests.get(requesterId);
        if (pendingTarget == null || !pendingTarget.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(
                    new ErrorResponse("No pending alliance request from "
                            + playerNames.getOrDefault(requesterId, requesterId) + ".")));
            return;
        }

        pendingAllianceRequests.remove(requesterId);

        if (req.isAccepted()) {
            setDiplomaticStatus(requesterId, clientId, "Allied");
            setDiplomaticStatus(clientId, requesterId, "Allied");

            String msg = String.format("🤝 %s and %s have formed an alliance!",
                    playerNames.getOrDefault(requesterId, requesterId),
                    playerNames.getOrDefault(clientId, clientId));

            server.broadcast(gson.toJson(new DiplomacyBroadcast(
                    "ALLIANCE_FORMED",
                    requesterId, playerNames.getOrDefault(requesterId, "?"),
                    clientId,    playerNames.getOrDefault(clientId, "?"),
                    msg)));

            // Fog of War is now shared — broadcastCustomizedStates picks this up via FogOfWarFilter
            broadcastCustomizedStates();

        } else {
            // Rejected
            String rejectMsg = playerNames.getOrDefault(clientId, clientId)
                    + " rejected your alliance request.";
            server.sendToClient(requesterId, gson.toJson(new DiplomacyBroadcast(
                    "ALLIANCE_REJECTED",
                    clientId,    playerNames.getOrDefault(clientId, "?"),
                    requesterId, playerNames.getOrDefault(requesterId, "?"),
                    rejectMsg)));

            server.sendToClient(clientId, gson.toJson(new GameNotificationMessage(
                    "❌ Alliance request from "
                            + playerNames.getOrDefault(requesterId, requesterId) + " rejected.")));
        }
    }

    // ─── Item Use ─────────────────────────────────────────────────────────────

    public synchronized void handleItemUseRequest(String clientId, ItemUseRequest req) {
        if (!isActivePlayer(clientId)) {
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
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Hex sourceHex = masterMap.getHexAt(req.getSourceQ(), req.getSourceR());
        Hex targetHex = masterMap.getHexAt(req.getTargetQ(), req.getTargetR());

        if (sourceHex == null || targetHex == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid hex coordinates.")));
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
            // B13: cannot attack allies
            if ("Allied".equals(diploStatus)) {
                server.sendToClient(clientId, gson.toJson(
                        new ErrorResponse("You cannot attack your ally!")));
                return;
            }
            // Must be at war to attack another player
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

        // B14: record war report for the defender if attack was on a player
        if (result != -1 && targetOwnerId != null && !targetOwnerId.equals(clientId)) {
            // Count units the defender lost
            long aliveAfter = masterMap.getUnits().stream()
                    .filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId()))
                    .count();
            // We stored count before attack at the top of the method — use siegeDmg for siege attacks
            int unitsLost = isSiegeAttack ? 0 : Math.max(0, (int) result);
            int siegeDmg  = isSiegeAttack ? result : 0;

            WatReport report = new WatReport(
                    clientId, playerNames.getOrDefault(clientId, clientId),
                    targetHex.getQ(), targetHex.getR(),
                    unitsLost, siegeDmg, isSiegeAttack);
            pendingWatReports.computeIfAbsent(targetOwnerId, k -> new ArrayList<>()).add(report);
        }

        broadcastCustomizedStates();
    }

    // ─── Broadcast (Fog-of-War per player) ───────────────────────────────────

    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();

            // B13: compute this player's allies for shared FoW
            Set<String> alliedPlayerIds = getAlliedPlayerIds(clientId);

            String filteredMapJson = fogOfWarFilter.filterForPlayer(
                    masterMap, clientId, alliedPlayerIds);

            GameStateBroadcast update = new GameStateBroadcast(
                    activePlayerId, masterMap.getCurrentTurn(), filteredMapJson);
            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    // ─── Player Elimination ───────────────────────────────────────────────────

    private void checkPlayerElimination(String playerId) {
        boolean hasActiveTH = masterMap.getHexes().stream().anyMatch(h ->
                h.getBuilding() != null
                        && h.getBuilding().getType() == BuildingType.TOWN_HALL
                        && !h.getBuilding().isDestroyed()
                        && playerId.equals(h.getBuilding().getOwnerId()));

        if (!hasActiveTH) {
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

            // Return all pending trade offers for this player
            cancelAllTradesForPlayer(playerId);

            server.broadcast(gson.toJson(new GameNotificationMessage(
                    "💀 " + playerNames.getOrDefault(playerId, playerId)
                            + " has been eliminated from the game!")));
        }
    }

    // ─── Diplomacy Helpers ────────────────────────────────────────────────────

    private void setDiplomaticStatus(String fromId, String toId, String status) {
        diplomacyStates.computeIfAbsent(fromId, k -> new ConcurrentHashMap<>())
                .put(toId, status);
    }

    private String getDiplomaticStatus(String playerId1, String playerId2) {
        if (playerId1 == null || playerId2 == null) return "Neutral";
        ConcurrentHashMap<String, String> relations = diplomacyStates.get(playerId1);
        if (relations == null) return "Neutral";
        return relations.getOrDefault(playerId2, "Neutral");
    }

    private boolean isValidTarget(String clientId, String targetId) {
        if (targetId == null || targetId.equals(clientId)) return false;
        return players.stream().anyMatch(p -> p.getId().equals(targetId));
    }

    /** Returns the set of playerIds that are allied with the given player. */
    private Set<String> getAlliedPlayerIds(String playerId) {
        Set<String> allies = new HashSet<>();
        ConcurrentHashMap<String, String> relations = diplomacyStates.get(playerId);
        if (relations == null) return allies;
        for (Map.Entry<String, String> entry : relations.entrySet()) {
            if ("Allied".equals(entry.getValue())) {
                allies.add(entry.getKey());
            }
        }
        return allies;
    }

    // ─── Trade Helpers ────────────────────────────────────────────────────────

    private void sendTradeInboxUpdate(String playerId) {
        List<TradeOffer> inbox = tradeInboxes.getOrDefault(playerId, new ArrayList<>());
        server.sendToClient(playerId, gson.toJson(new TradeInboxBroadcast(
                new ArrayList<>(inbox))));
    }

    private void removeTradeOffer(TradeOffer offer, String inboxOwnerId) {
        List<TradeOffer> inbox = tradeInboxes.get(inboxOwnerId);
        if (inbox != null) {
            inbox.removeIf(o -> o.getId().equals(offer.getId()));
        }
    }

    /** Cancels all trades between two players (e.g. on war declaration). */
    private void cancelTradesBetween(String playerA, String playerB) {
        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            List<TradeOffer> toRemove = new ArrayList<>();
            for (TradeOffer offer : entry.getValue()) {
                if ((offer.getOffererId().equals(playerA) && offer.getTargetId().equals(playerB))
                        || (offer.getOffererId().equals(playerB) && offer.getTargetId().equals(playerA))) {
                    // Unlock the offerer's resources
                    Inventory inv = getPlayerInventory(offer.getOffererId());
                    if (inv != null) {
                        inv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
                    }
                    toRemove.add(offer);
                }
            }
            entry.getValue().removeAll(toRemove);
        }
        // Update inboxes for affected players
        sendTradeInboxUpdate(playerA);
        sendTradeInboxUpdate(playerB);
    }

    /** Cancels ALL pending trades involving the eliminated player. */
    private void cancelAllTradesForPlayer(String playerId) {
        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            List<TradeOffer> toRemove = new ArrayList<>();
            for (TradeOffer offer : entry.getValue()) {
                if (offer.getOffererId().equals(playerId) || offer.getTargetId().equals(playerId)) {
                    if (offer.getOffererId().equals(playerId)) {
                        Inventory inv = getPlayerInventory(playerId);
                        if (inv != null) inv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
                    }
                    toRemove.add(offer);
                }
            }
            entry.getValue().removeAll(toRemove);
        }
    }

    // ─── General Helpers ──────────────────────────────────────────────────────

    private boolean isActivePlayer(String clientId) {
        return players.get(currentPlayerIndex).getId().equals(clientId);
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

    // ─── War Report Delivery (B14) ────────────────────────────────────────────

    /**
     * Sends all pending war reports to the given player and clears the queue. (B14)
     * Called at the start of each player's turn so they know what happened while waiting.
     */
    private void deliverWatReports(String playerId) {
        List<WatReport> reports = pendingWatReports.get(playerId);
        if (reports == null || reports.isEmpty()) return;

        server.sendToClient(playerId, gson.toJson(new WatReportBroadcast(new ArrayList<>(reports))));
        reports.clear();
    }
}