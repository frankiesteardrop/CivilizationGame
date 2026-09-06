package network.server;

import com.google.gson.Gson;
import controller.BuildController;
import controller.CombatController;
import controller.UnitController;
import controller.UpgradeController;
import model.*;
import model.maps.MapDefinition;
import model.maps.PreDesignedMaps;
import network.messages.game.*;
import network.messages.lobby.LobbyPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager {

    private final GameServer server;
    private final Gson gson;

    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingAllianceRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TradeOffer>> tradeInboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<WatReport>> pendingWatReports = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new HashMap<>();

    private ServerTurnProcessor serverTurnProcessor;
    private final String selectedMapId;
    private FogOfWarFilter fogOfWarFilter;

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers, String selectedMapId) {
        this.server             = server;
        this.gson               = new Gson();
        this.players            = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
        this.selectedMapId      = selectedMapId;
    }

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

            playerNames.put(playerId, players.get(i).getUsername());
            tradeInboxes.put(playerId, new ArrayList<>());
            pendingWatReports.put(playerId, new ArrayList<>());

            System.out.println("[Server] Player " + players.get(i).getUsername() + " spawned at (" + spawn[0] + "," + spawn[1] + ")");
        }

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

        String nextPlayerId = players.get(currentPlayerIndex).getId();
        deliverWatReports(nextPlayerId);

        broadcastCustomizedStates();
    }

    public synchronized void handlePlayerDisconnected(String clientId) {
        if (serverTurnProcessor == null) return;

        String playerName = playerNames.getOrDefault(clientId, clientId);
        System.out.println("[Server] Player disconnected during game: " + playerName);

        cancelAllTradesForPlayer(clientId);

        server.broadcast(gson.toJson(new GameNotificationMessage("⚠️ Player " + playerName + " has disconnected.")));

        if (players.get(currentPlayerIndex).getId().equals(clientId)) {
            server.broadcast(gson.toJson(new GameNotificationMessage("⏭️ " + playerName + "'s turn was automatically skipped.")));

            serverTurnProcessor.processTurn(masterMap);

            currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
            if (currentPlayerIndex == 0) {
                masterMap.incrementTurn();
            }

            String nextPlayerId = players.get(currentPlayerIndex).getId();
            deliverWatReports(nextPlayerId);

            broadcastCustomizedStates();
        }
    }

    public synchronized void handleCraftItemRequest(String clientId, CraftItemRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Hex hex = masterMap.getHexAt(req.getApothecaryQ(), req.getApothecaryR());
        if (hex == null || !(hex.getBuilding() instanceof Apothecary apothecary)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No Apothecary found at the specified location.")));
            return;
        }

        if (!clientId.equals(hex.getBuilding().getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not own this Apothecary.")));
            return;
        }

        if (!apothecary.canQueueItem()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("This Apothecary is already crafting: " + apothecary.getCurrentlyCrafting())));
            return;
        }

        String itemName = req.getItemName();
        if (!Apothecary.ItemType.isValid(itemName)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unknown item type: " + itemName)));
            return;
        }

        Apothecary.ItemType itemType;
        try {
            itemType = Apothecary.ItemType.valueOf(itemName);
        } catch (IllegalArgumentException e) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid item name.")));
            return;
        }

        Inventory inv = getPlayerInventory(clientId);
        if (inv == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot find player inventory.")));
            return;
        }

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

        inv.consumeResource(ResourceType.FOOD,  itemType.getFoodCost());
        inv.consumeResource(ResourceType.STONE, itemType.getStoneCost());
        inv.consumeResource(ResourceType.IRON,  itemType.getIronCost());
        inv.consumeResource(ResourceType.WOOD,  itemType.getWoodCost());
        apothecary.queueItem(itemName);

        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("⚗️ Crafting " + itemType.getDisplayName() + " — will be ready at end of turn!")));
        broadcastCustomizedStates();
    }

    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        String targetId = req.getTargetPlayerId();
        if (targetId == null || targetId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid trade target.")));
            return;
        }

        boolean targetExists = players.stream().anyMatch(p -> p.getId().equals(targetId));
        if (!targetExists) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Target player not found.")));
            return;
        }

        if (req.getOfferAmount() <= 0 || req.getRequestAmount() <= 0) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade amounts must be greater than zero.")));
            return;
        }

        Inventory offererInv = getPlayerInventory(clientId);
        if (offererInv == null || !offererInv.hasEnough(req.getOfferType(), req.getOfferAmount())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not have enough " + req.getOfferType() + " to offer! Need: " + req.getOfferAmount())));
            return;
        }

        offererInv.lockResource(req.getOfferType(), req.getOfferAmount());

        String offererName = playerNames.getOrDefault(clientId, clientId);
        TradeOffer offer = new TradeOffer(clientId, offererName, targetId,
                req.getOfferType(), req.getOfferAmount(),
                req.getRequestType(), req.getRequestAmount());

        tradeInboxes.computeIfAbsent(targetId, k -> new ArrayList<>()).add(offer);

        sendTradeInboxUpdate(targetId);
        server.sendToClient(targetId, gson.toJson(new GameNotificationMessage("📬 " + offererName + " has sent you a trade offer!")));
        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("✅ Trade offer sent to " + playerNames.getOrDefault(targetId, targetId) + ".")));

        broadcastCustomizedStates();
    }

    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        String tradeId = req.getTradeId();

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
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade offer not found. It may have been cancelled.")));
            return;
        }

        boolean isTarget  = clientId.equals(offer.getTargetId());
        boolean isOfferer = clientId.equals(offer.getOffererId());

        if (!isTarget && !isOfferer) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You are not a party to this trade offer.")));
            return;
        }

        if (isOfferer && !req.isAccepted()) {
            removeTradeOffer(offer, inboxOwnerId);
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            if (offererInv != null) {
                offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
            }
            server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("🚫 Trade offer cancelled. Resources returned.")));
            sendTradeInboxUpdate(offer.getTargetId());
            broadcastCustomizedStates();
            return;
        }

        if (!isTarget) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Only the target player can accept or reject this offer.")));
            return;
        }

        if (!req.isAccepted()) {
            removeTradeOffer(offer, inboxOwnerId);
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            if (offererInv != null) {
                offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
            }
            server.sendToClient(offer.getOffererId(), gson.toJson(new GameNotificationMessage("❌ " + playerNames.getOrDefault(clientId, clientId) + " rejected your trade offer. Resources returned.")));
            sendTradeInboxUpdate(clientId);
            broadcastCustomizedStates();
            return;
        }

        Inventory targetInv = getPlayerInventory(clientId);
        if (targetInv == null || !targetInv.hasEnough(offer.getRequestType(), offer.getRequestAmount())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not have enough " + offer.getRequestType() + " to accept this trade! Need: " + offer.getRequestAmount())));
            return;
        }

        targetInv.consumeResource(offer.getRequestType(), offer.getRequestAmount());
        Inventory offererInv = getPlayerInventory(offer.getOffererId());
        if (offererInv != null) {
            offererInv.consumeLockedResource(offer.getOfferType(), offer.getOfferAmount());
        }
        targetInv.addResource(offer.getOfferType(), offer.getOfferAmount());
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

    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        String targetId = req.getTargetPlayerId();
        if (!isValidTarget(clientId, targetId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid diplomacy target.")));
            return;
        }

        switch (req.getAction()) {
            case "DECLARE_WAR" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if ("Enemy".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("You are already at war with this player.")));
                    return;
                }

                setDiplomaticStatus(clientId, targetId, "Enemy");
                setDiplomaticStatus(targetId, clientId, "Enemy");

                pendingAllianceRequests.remove(clientId);
                pendingAllianceRequests.entrySet().removeIf(e -> e.getKey().equals(targetId) && e.getValue().equals(clientId));

                cancelTradesBetween(clientId, targetId);

                String msg = String.format("⚔️ %s has declared war on %s!",
                        playerNames.getOrDefault(clientId, clientId),
                        playerNames.getOrDefault(targetId, targetId));

                server.broadcast(gson.toJson(new DiplomacyBroadcast("WAR_DECLARED", clientId, playerNames.getOrDefault(clientId, "?"), targetId, playerNames.getOrDefault(targetId, "?"), msg)));
                broadcastCustomizedStates();
            }

            case "REQUEST_ALLIANCE" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if ("Enemy".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot request alliance while at war. Declare peace first.")));
                    return;
                }
                if ("Allied".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("You are already allied with this player.")));
                    return;
                }
                if (pendingAllianceRequests.containsKey(clientId) && pendingAllianceRequests.get(clientId).equals(targetId)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("You already have a pending alliance request to this player.")));
                    return;
                }

                pendingAllianceRequests.put(clientId, targetId);

                String requestMsg = String.format("🤝 %s has sent you an alliance request!", playerNames.getOrDefault(clientId, clientId));
                server.sendToClient(targetId, gson.toJson(new DiplomacyBroadcast("ALLIANCE_REQUESTED", clientId, playerNames.getOrDefault(clientId, "?"), targetId, playerNames.getOrDefault(targetId, "?"), requestMsg)));
                server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("🤝 Alliance request sent to " + playerNames.getOrDefault(targetId, targetId) + ".")));
            }

            case "BREAK_ALLIANCE" -> {
                String currentStatus = getDiplomaticStatus(clientId, targetId);
                if (!"Allied".equals(currentStatus)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("You are not allied with this player.")));
                    return;
                }

                setDiplomaticStatus(clientId, targetId, "Neutral");
                setDiplomaticStatus(targetId, clientId, "Neutral");

                String msg = String.format("💔 %s has broken the alliance with %s!", playerNames.getOrDefault(clientId, clientId), playerNames.getOrDefault(targetId, targetId));

                server.broadcast(gson.toJson(new DiplomacyBroadcast("ALLIANCE_BROKEN", clientId, playerNames.getOrDefault(clientId, "?"), targetId, playerNames.getOrDefault(targetId, "?"), msg)));
                broadcastCustomizedStates();
            }

            default -> server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unknown diplomacy action: " + req.getAction())));
        }
    }

    public synchronized void handleAllianceResponse(String clientId, AllianceResponseRequest req) {
        String requesterId = req.getRequesterId();

        String pendingTarget = pendingAllianceRequests.get(requesterId);
        if (pendingTarget == null || !pendingTarget.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No pending alliance request from " + playerNames.getOrDefault(requesterId, requesterId) + ".")));
            return;
        }

        pendingAllianceRequests.remove(requesterId);

        if (req.isAccepted()) {
            setDiplomaticStatus(requesterId, clientId, "Allied");
            setDiplomaticStatus(clientId, requesterId, "Allied");

            String msg = String.format("🤝 %s and %s have formed an alliance!", playerNames.getOrDefault(requesterId, requesterId), playerNames.getOrDefault(clientId, clientId));
            server.broadcast(gson.toJson(new DiplomacyBroadcast("ALLIANCE_FORMED", requesterId, playerNames.getOrDefault(requesterId, "?"), clientId, playerNames.getOrDefault(clientId, "?"), msg)));
            broadcastCustomizedStates();
        } else {
            String rejectMsg = playerNames.getOrDefault(clientId, clientId) + " rejected your alliance request.";
            server.sendToClient(requesterId, gson.toJson(new DiplomacyBroadcast("ALLIANCE_REJECTED", clientId, playerNames.getOrDefault(clientId, "?"), requesterId, playerNames.getOrDefault(requesterId, "?"), rejectMsg)));
            server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("❌ Alliance request from " + playerNames.getOrDefault(requesterId, requesterId) + " rejected.")));
        }
    }

    public synchronized void handleItemUseRequest(String clientId, ItemUseRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Unit target = masterMap.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == req.getTargetQ() && u.getR() == req.getTargetR()).findFirst().orElse(null);

        if (target == null || !clientId.equals(target.getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You can only use items on your own units!")));
            return;
        }
        if (target.hasUsedItemThisTurn()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("This unit has already used an item this turn!")));
            return;
        }

        Inventory playerInv = getPlayerInventory(clientId);
        if (playerInv == null || !playerInv.hasItem(req.getItemName())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not have this item in your inventory!")));
            return;
        }

        // 1. اعتبارسنجی کامل پیش از جهش (Validation Block)
        switch (req.getItemName()) {
            case "TELEPORT" -> {
                Hex dest = masterMap.getHexAt(req.getDestQ(), req.getDestR());
                if (dest == null) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("مقصد خارج از نقشه است.")));
                    return;
                }
                if (masterMap.hasUnitAt(dest.getQ(), dest.getR())) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("hex مقصد اشغال است.")));
                    return;
                }
                if (!dest.isExplored(clientId)) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("این منطقه کشف نشده است.")));
                    return;
                }
                if (dest.getTerrainType() == TerrainType.MOUNTAIN_RANGE || dest.getTerrainType() == TerrainType.SEA) {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("terrain غیرقابل عبور برای این unit.")));
                    return;
                }
            }
        }

        // 2. اعمال تغییرات وضعیت (Mutation Block)
        playerInv.consumeItem(req.getItemName());
        target.setUsedItemThisTurn(true);

        // 3. اجرای نهایی (Execution Block)
        switch (req.getItemName()) {
            case "TELEPORT" -> target.moveTo(req.getDestQ(), req.getDestR(), 0);
            case "MOBILITY" -> target.addTemporaryAP(2);
            case "COMBAT"   -> {
                target.setTemporaryCombatDiceBonus(1);
                target.setTemporarySiegeBonus(5);
            }
        }

        broadcastCustomizedStates();
    }

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
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not own these units!")));
                    return;
                }
                attackers.add(u);
            }
        }

        if (attackers.isEmpty()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No valid attacking units found.")));
            return;
        }

        String targetOwnerId = getTargetOwnerId(targetHex);

        if (targetOwnerId != null && targetOwnerId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You cannot attack your own units or structures!")));
            return;
        }

        if (targetOwnerId != null) {
            String diploStatus = getDiplomaticStatus(clientId, targetOwnerId);
            if ("Allied".equals(diploStatus)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You cannot attack your ally!")));
                return;
            }
            if (!"Enemy".equals(diploStatus)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You must declare war first! Current diplomatic status: " + diploStatus)));
                return;
            }
        }

        boolean isTargetAnimal = masterMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getType() == UnitType.BEAR && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR());
        boolean hasEnemyUnit = masterMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && !clientId.equals(u.getOwnerId()));
        boolean isSiegeAttack = !hasEnemyUnit && !isTargetAnimal;

        boolean targetHasWall = false;
        for (int i = 0; i < 6; i++) {
            if (masterMap.getNeighbor(sourceHex, i) == targetHex) {
                targetHasWall = sourceHex.hasWall(i);
                break;
            }
        }

        CombatController cc = new CombatController(masterMap);
        int result = cc.executeAttack(attackers, sourceHex, targetHex, isSiegeAttack, isTargetAnimal, targetHasWall);

        if (result == -1) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack failed. Check AP or attack range.")));
            return;
        }

        if (targetOwnerId != null && isSiegeAttack) {
            checkPlayerElimination(targetOwnerId);
        }

        if (result != -1 && targetOwnerId != null && !targetOwnerId.equals(clientId)) {
            long aliveAfter = masterMap.getUnits().stream().filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId())).count();
            int unitsLost = isSiegeAttack ? 0 : Math.max(0, (int) result);
            int siegeDmg  = isSiegeAttack ? result : 0;

            WatReport report = new WatReport(clientId, playerNames.getOrDefault(clientId, clientId), targetHex.getQ(), targetHex.getR(), unitsLost, siegeDmg, isSiegeAttack);
            pendingWatReports.computeIfAbsent(targetOwnerId, k -> new ArrayList<>()).add(report);
        }

        broadcastCustomizedStates();
    }

    public synchronized void handleMoveRequest(String clientId, MoveRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        Unit unit = masterMap.getUnits().stream()
                .filter(u -> u.getQ() == req.getSrcQ() && u.getR() == req.getSrcR() && clientId.equals(u.getOwnerId()))
                .findFirst().orElse(null);
        Hex target = masterMap.getHexAt(req.getTgtQ(), req.getTgtR());

        if (unit != null && target != null) {
            UnitController uc = new UnitController();
            if (uc.canMove(unit, target, masterMap)) {
                uc.executeMove(unit, target, masterMap);
                broadcastCustomizedStates();
                return;
            }
        }
        server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid move.")));
    }

    public synchronized void handleBuildRequest(String clientId, BuildRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        Unit u = masterMap.getUnits().stream()
                .filter(x -> x.getQ() == req.getUnitQ() && x.getR() == req.getUnitR() && clientId.equals(x.getOwnerId()))
                .findFirst().orElse(null);
        Hex target = masterMap.getHexAt(req.getHexQ(), req.getHexR());

        if (u != null && target != null) {
            if ("STATION".equals(req.getActionType()) && u instanceof Worker worker) {
                UnitController uc = new UnitController();
                if (uc.canStation(worker, target, masterMap)) {
                    uc.handleStation(worker, target, masterMap, null);
                    broadcastCustomizedStates();
                    return;
                }
            } else if ("EJECT".equals(req.getActionType()) && u instanceof Worker worker) {
                UnitController uc = new UnitController();
                if (uc.canEject(worker)) {
                    uc.handleEject(worker, null);
                    broadcastCustomizedStates();
                    return;
                }
            } else if (u instanceof Builder builder) {
                BuildController bc = new BuildController(masterMap);
                switch (req.getActionType()) {
                    case "BUILD" -> {
                        BuildingType type = BuildingType.valueOf(req.getStructureType());
                        if (bc.canBuild(type, target, builder)) {
                            bc.buildStructure(builder, type, target, null);
                            broadcastCustomizedStates();
                            return;
                        }
                    }
                    case "ROAD" -> {
                        if (bc.canBuildRoad(target, builder)) {
                            bc.buildRoad(builder, target, null);
                            broadcastCustomizedStates();
                            return;
                        }
                    }
                    case "WALL" -> {
                        if (bc.canBuildWall(target, req.getDir(), builder)) {
                            bc.buildWall(builder, target, req.getDir(), null);
                            broadcastCustomizedStates();
                            return;
                        }
                    }
                    case "DESTROY" -> {
                        if (bc.canDestroy(target, req.getStructureType(), req.getDir(), builder)) {
                            bc.destroyStructure(builder, target, req.getStructureType(), req.getDir(), null);
                            broadcastCustomizedStates();
                            return;
                        }
                    }
                }
            }
        }
        server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid build/action request.")));
    }

    public synchronized void handleTrainRequest(String clientId, TrainRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
        UpgradeController uc = new UpgradeController(masterMap);
        String type = req.getUnitType();

        boolean success = false;
        if ("UPGRADE_TH".equals(type)) {
            if (uc.canAffordWarehouseUpgrade()) {
                uc.handleWarehouseUpgrade(null);
                success = true;
            }
        } else if (type.startsWith("TECH:")) {
            String tech = type.substring(5);
            if (uc.canUnlockTech(tech)) {
                uc.unlockTech(tech, null);
                success = true;
            }
        } else {
            if (uc.canTrainUnit(type)) {
                uc.trainUnit(type, null);
                success = true;
            }
        }

        masterMap.clearActiveTownHall();
        if (success) {
            broadcastCustomizedStates();
        } else {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Training/Upgrade failed. Check resources.")));
        }
    }

    public synchronized void handleCancelProduction(String clientId) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        TownHall th = masterMap.getPlayerTownHall(clientId);
        if (!th.isProductionQueueEmpty()) {
            th.cancelCurrentProduction();
            broadcastCustomizedStates();
        } else {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Queue is already empty.")));
        }
    }

    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            Set<String> alliedPlayerIds = getAlliedPlayerIds(clientId);

            String filteredMapJson = fogOfWarFilter.filterForPlayer(masterMap, clientId, alliedPlayerIds);

            GameStateBroadcast update = new GameStateBroadcast(activePlayerId, masterMap.getCurrentTurn(), filteredMapJson);
            server.sendToClient(clientId, gson.toJson(update));
        }
    }

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

            cancelAllTradesForPlayer(playerId);

            server.broadcast(gson.toJson(new GameNotificationMessage("💀 " + playerNames.getOrDefault(playerId, playerId) + " has been eliminated from the game!")));
        }
    }

    private void setDiplomaticStatus(String fromId, String toId, String status) {
        diplomacyStates.computeIfAbsent(fromId, k -> new ConcurrentHashMap<>()).put(toId, status);
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

    private void sendTradeInboxUpdate(String playerId) {
        List<TradeOffer> inbox = tradeInboxes.getOrDefault(playerId, new ArrayList<>());
        server.sendToClient(playerId, gson.toJson(new TradeInboxBroadcast(new ArrayList<>(inbox))));
    }

    private void removeTradeOffer(TradeOffer offer, String inboxOwnerId) {
        List<TradeOffer> inbox = tradeInboxes.get(inboxOwnerId);
        if (inbox != null) {
            inbox.removeIf(o -> o.getId().equals(offer.getId()));
        }
    }

    private void cancelTradesBetween(String playerA, String playerB) {
        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            List<TradeOffer> toRemove = new ArrayList<>();
            for (TradeOffer offer : entry.getValue()) {
                if ((offer.getOffererId().equals(playerA) && offer.getTargetId().equals(playerB))
                        || (offer.getOffererId().equals(playerB) && offer.getTargetId().equals(playerA))) {
                    Inventory inv = getPlayerInventory(offer.getOffererId());
                    if (inv != null) {
                        inv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
                    }
                    toRemove.add(offer);
                }
            }
            entry.getValue().removeAll(toRemove);
        }
        sendTradeInboxUpdate(playerA);
        sendTradeInboxUpdate(playerB);
    }

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

    private boolean isActivePlayer(String clientId) {
        return players.get(currentPlayerIndex).getId().equals(clientId);
    }

    private Inventory getPlayerInventory(String ownerId) {
        for (Hex h : masterMap.getHexes()) {
            if (h.getBuilding() != null && h.getBuilding().getType() == BuildingType.TOWN_HALL && ownerId.equals(h.getBuilding().getOwnerId())) {
                return ((TownHall) h.getBuilding()).getInventory();
            }
        }
        return masterMap.getTownHall().getInventory();
    }

    private String getTargetOwnerId(Hex targetHex) {
        for (Unit u : masterMap.getUnits()) {
            if (u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && u.getOwnerId() != null) {
                return u.getOwnerId();
            }
        }
        if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
            return targetHex.getBuilding().getOwnerId();
        }
        return null;
    }

    private void deliverWatReports(String playerId) {
        List<WatReport> reports = pendingWatReports.get(playerId);
        if (reports == null || reports.isEmpty()) return;

        server.sendToClient(playerId, gson.toJson(new WatReportBroadcast(new ArrayList<>(reports))));
        reports.clear();
    }
}