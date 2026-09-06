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
    private final DatabaseManager databaseManager;
    private final Gson gson;

    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> pendingAllianceRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TradeOffer>> tradeInboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<WarReport>> pendingWarReports = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new HashMap<>();

    private ServerTurnProcessor serverTurnProcessor;
    private final String selectedMapId;
    private FogOfWarFilter fogOfWarFilter;

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers, String selectedMapId, DatabaseManager databaseManager) {
        this.server             = server;
        this.databaseManager    = databaseManager;
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
            pendingWarReports.put(playerId, new ArrayList<>());
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
        server.broadcast(gson.toJson(new GameStartBroadcast()));

        server.broadcast(gson.toJson(new DiplomacyBroadcast("GAME_START", "server", "Server", "all", "All", "Game Initialized")));

        broadcastCustomizedStates();
    }

    public synchronized void handleEndTurn(String clientId) {
        if (!isActivePlayer(clientId)) return;
        serverTurnProcessor.processPlayerTurnEnd(masterMap, clientId);
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        boolean isNewRound = (currentPlayerIndex == 0);
        if (isNewRound) {
            masterMap.incrementTurn();
            serverTurnProcessor.processGlobalRoundEnd(masterMap);
        }
        databaseManager.saveGameSession("active_match", gson.toJson(masterMap));
        String nextPlayerId = players.get(currentPlayerIndex).getId();
        deliverWarReports(nextPlayerId);
        broadcastCustomizedStates();
    }

    public synchronized void handlePlayerDisconnected(String clientId) {
        if (serverTurnProcessor == null) return;
        removePlayerFully(clientId, "DISCONNECT");
    }

    private void checkPlayerElimination(String playerId) {
        boolean hasActiveTH = masterMap.getHexes().stream().anyMatch(h ->
                h.getBuilding() != null && h.getBuilding().getType() == BuildingType.TOWN_HALL
                        && !h.getBuilding().isDestroyed() && playerId.equals(h.getBuilding().getOwnerId()));
        if (!hasActiveTH) removePlayerFully(playerId, "ELIMINATION");
    }

    private void removePlayerFully(String playerId, String reasonType) {
        boolean wasActivePlayer = (!players.isEmpty() && players.get(currentPlayerIndex).getId().equals(playerId));
        String name = playerNames.getOrDefault(playerId, playerId);

        masterMap.removeUnitsWhere(u -> playerId.equals(u.getOwnerId()));
        for (Hex h : masterMap.getHexes()) {
            if (h.getBuilding() != null && playerId.equals(h.getBuilding().getOwnerId())) {
                h.getBuilding().takeDamage(9999);
                h.setBuilding(null);
            }
        }
        cancelAllTradesForPlayer(playerId);
        diplomacyStates.remove(playerId);
        tradeInboxes.remove(playerId);
        pendingWarReports.remove(playerId);

        if ("DISCONNECT".equals(reasonType)) {
            server.broadcast(gson.toJson(new GameNotificationMessage("⚠️ Player " + name + " disconnected and has been removed from the match.")));
        } else {
            server.broadcast(gson.toJson(new GameNotificationMessage("💀 " + name + " has been eliminated from the game!")));
        }

        removePlayerFromTurnOrder(playerId);

        if (players.size() == 1) {
            String winnerName = playerNames.getOrDefault(players.get(0).getId(), "Unknown");
            server.broadcast(gson.toJson(new GameNotificationMessage("🏆 " + winnerName + " has won the game!")));
            return;
        }

        if (wasActivePlayer && !players.isEmpty()) {
            server.broadcast(gson.toJson(new GameNotificationMessage("⏭️ " + name + "'s turn was automatically skipped.")));
            serverTurnProcessor.processPlayerTurnEnd(masterMap, playerId);
            boolean isNewRound = (currentPlayerIndex == 0);
            if (isNewRound) {
                masterMap.incrementTurn();
                serverTurnProcessor.processGlobalRoundEnd(masterMap);
            }
            String nextPlayerId = players.get(currentPlayerIndex).getId();
            deliverWarReports(nextPlayerId);
            broadcastCustomizedStates();
        } else if (!players.isEmpty()) {
            broadcastCustomizedStates();
        }
    }

    private void removePlayerFromTurnOrder(String playerId) {
        int removedIndex = -1;
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).getId().equals(playerId)) { removedIndex = i; break; }
        }
        if (removedIndex == -1) return;
        players.remove(removedIndex);
        if (players.isEmpty()) return;
        if (removedIndex < currentPlayerIndex) {
            currentPlayerIndex--;
        } else if (removedIndex == currentPlayerIndex) {
            if (currentPlayerIndex >= players.size()) currentPlayerIndex = 0;
        }
    }

    public synchronized void handleCancelTrade(String clientId, CancelTradeRequest req) {
        TradeOffer offerToCancel = null;
        String targetPlayerId = null;

        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            for (TradeOffer offer : entry.getValue()) {
                if (offer.getId().equals(req.getTradeId()) && offer.getOffererId().equals(clientId)) {
                    offerToCancel = offer;
                    targetPlayerId = entry.getKey();
                    break;
                }
            }
            if (offerToCancel != null) break;
        }

        if (offerToCancel == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade offer not found or you don't own it.")));
            return;
        }

        Inventory inv = getPlayerInventory(clientId);
        if (inv != null) inv.unlockResource(offerToCancel.getOfferType(), offerToCancel.getOfferAmount());

        tradeInboxes.get(targetPlayerId).remove(offerToCancel);
        sendTradeInboxUpdate(targetPlayerId);

        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("🚫 You canceled your trade offer. Resources unlocked.")));
        server.sendToClient(targetPlayerId, gson.toJson(new GameNotificationMessage("🚫 " + playerNames.get(clientId) + " canceled their trade offer.")));
        broadcastCustomizedStates();
    }

    public synchronized void handleAttackRequest(String clientId, AttackRequest req) {
        if (!isActivePlayer(clientId)) return;
        Hex sourceHex = masterMap.getHexAt(req.getSourceQ(), req.getSourceR());
        Hex targetHex = masterMap.getHexAt(req.getTargetQ(), req.getTargetR());
        if (sourceHex == null || targetHex == null) return;

        List<Unit> attackers = new ArrayList<>();
        for (Unit u : masterMap.getUnits()) {
            if (u.isAlive() && u.getQ() == sourceHex.getQ() && u.getR() == sourceHex.getR() && clientId.equals(u.getOwnerId())) {
                attackers.add(u);
            }
        }
        if (attackers.isEmpty()) return;

        String targetOwnerId = getTargetOwnerId(targetHex);

        long aliveBefore = 0;
        if (targetOwnerId != null) {
            aliveBefore = masterMap.getUnits().stream()
                    .filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId())).count();
        }

        CombatController cc = new CombatController(masterMap);
        boolean isTargetAnimal = masterMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getType() == UnitType.BEAR && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR());
        boolean hasEnemyUnit = masterMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && !clientId.equals(u.getOwnerId()));
        boolean isSiegeAttack = !hasEnemyUnit && !isTargetAnimal;
        boolean targetHasWall = false;
        for (int i = 0; i < 6; i++) {
            if (masterMap.getNeighbor(sourceHex, i) == targetHex) {
                targetHasWall = sourceHex.hasWall(i); break;
            }
        }

        CombatResult result = cc.executeAttack(attackers, sourceHex, targetHex, isSiegeAttack, isTargetAnimal, targetHasWall);
        if (result == null) return;

        if (targetOwnerId != null && isSiegeAttack) {
            checkPlayerElimination(targetOwnerId);
        }

        if (targetOwnerId != null && !targetOwnerId.equals(clientId)) {
            long aliveAfter = masterMap.getUnits().stream()
                    .filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId())).count();
            int unitsLost = (int) (aliveBefore - aliveAfter);

            WarReport report = new WarReport(
                    clientId, playerNames.getOrDefault(clientId, clientId),
                    targetHex.getQ(), targetHex.getR(),
                    unitsLost, result.attackerTakesDmg,
                    isSiegeAttack ? result.defenderTakesDmg : 0,
                    isSiegeAttack,
                    result.attackerRolls, result.defenderRolls
            );
            pendingWarReports.computeIfAbsent(targetOwnerId, k -> new ArrayList<>()).add(report);
        }
        broadcastCustomizedStates();
    }

    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        if (!isActivePlayer(clientId)) return;
        String targetId = req.getTargetPlayerId();
        if (targetId == null || targetId.equals(clientId)) return;
        Inventory offererInv = getPlayerInventory(clientId);
        if (offererInv == null || !offererInv.hasEnough(req.getOfferType(), req.getOfferAmount())) return;
        offererInv.lockResource(req.getOfferType(), req.getOfferAmount());
        TradeOffer offer = new TradeOffer(clientId, playerNames.get(clientId), targetId,
                req.getOfferType(), req.getOfferAmount(), req.getRequestType(), req.getRequestAmount());
        tradeInboxes.computeIfAbsent(targetId, k -> new ArrayList<>()).add(offer);
        sendTradeInboxUpdate(targetId);
        broadcastCustomizedStates();
    }

    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        TradeOffer offer = null;
        String inboxOwnerId = null;
        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            for (TradeOffer o : entry.getValue()) {
                if (o.getId().equals(req.getTradeId())) { offer = o; inboxOwnerId = entry.getKey(); break; }
            }
            if (offer != null) break;
        }

        if (offer == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade offer not found or already processed.")));
            return;
        }

        boolean isTarget = clientId.equals(offer.getTargetId());

        if (isTarget && req.isAccepted()) {
            if (!isActivePlayer(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn! You can only ACCEPT trades during your turn.")));
                return;
            }

            Inventory targetInv = getPlayerInventory(clientId);
            if (targetInv == null || !targetInv.hasEnough(offer.getRequestType(), offer.getRequestAmount())) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You don't have enough resources to accept this trade anymore!")));
                return;
            }
        }

        if (req.isAccepted()) {
            Inventory targetInv = getPlayerInventory(clientId);
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            targetInv.consumeResource(offer.getRequestType(), offer.getRequestAmount());
            offererInv.consumeLockedResource(offer.getOfferType(), offer.getOfferAmount());
            targetInv.addResource(offer.getOfferType(), offer.getOfferAmount());
            offererInv.addResource(offer.getRequestType(), offer.getRequestAmount());
        } else {
            Inventory offererInv = getPlayerInventory(offer.getOffererId());
            if (offererInv != null) offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
        }
        removeTradeOffer(offer, inboxOwnerId);
        sendTradeInboxUpdate(clientId);
        broadcastCustomizedStates();
    }

    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        String targetId = req.getTargetPlayerId();
        if (!isValidTarget(clientId, targetId)) return;
        if ("DECLARE_WAR".equals(req.getAction())) {
            setDiplomaticStatus(clientId, targetId, "Enemy");
            setDiplomaticStatus(targetId, clientId, "Enemy");
            server.broadcast(gson.toJson(new DiplomacyBroadcast("WAR_DECLARED", clientId, playerNames.get(clientId), targetId, playerNames.get(targetId), "War declared!")));
            broadcastCustomizedStates();
        }
    }

    public synchronized void handleAllianceResponse(String clientId, AllianceResponseRequest req) {}

    public synchronized void handleItemUseRequest(String clientId, ItemUseRequest req) {
        if (!isActivePlayer(clientId)) return;
        Unit target = masterMap.getUnits().stream().filter(u -> u.isAlive() && u.getQ() == req.getTargetQ() && u.getR() == req.getTargetR()).findFirst().orElse(null);
        if (target == null || !clientId.equals(target.getOwnerId()) || target.hasUsedItemThisTurn()) return;
        Inventory playerInv = getPlayerInventory(clientId);
        if (playerInv == null || !playerInv.hasItem(req.getItemName())) return;

        if ("TELEPORT".equals(req.getItemName())) {
            Hex dest = masterMap.getHexAt(req.getDestQ(), req.getDestR());
            if (dest == null) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid destination.")));
                return;
            }
            if (masterMap.hasUnitAt(dest.getQ(), dest.getR())) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Destination is occupied.")));
                return;
            }
            if (!dest.isExplored(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot teleport to unexplored areas in your Fog of War.")));
                return;
            }
            if (dest.getTerrainType() == TerrainType.MOUNTAIN_RANGE || dest.getTerrainType() == TerrainType.SEA) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Terrain is impassable.")));
                return;
            }
        }

        playerInv.consumeItem(req.getItemName());
        target.setUsedItemThisTurn(true);
        if ("TELEPORT".equals(req.getItemName())) target.moveTo(req.getDestQ(), req.getDestR(), 0);

        broadcastCustomizedStates();
    }

    public synchronized void handleMoveRequest(String clientId, MoveRequest req) {
        if (!isActivePlayer(clientId)) return;
        Unit unit = masterMap.getUnits().stream().filter(u -> u.getQ() == req.getSrcQ() && u.getR() == req.getSrcR() && clientId.equals(u.getOwnerId())).findFirst().orElse(null);
        Hex target = masterMap.getHexAt(req.getTgtQ(), req.getTgtR());
        if (unit != null && target != null) {
            UnitController uc = new UnitController();
            if (uc.canMove(unit, target, masterMap)) {
                uc.executeMove(unit, target, masterMap);
                broadcastCustomizedStates();
            }
        }
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
            masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
            try {
                if ("STATION".equals(req.getActionType()) && u instanceof Worker worker) {
                    UnitController uc = new UnitController();
                    if (uc.canStation(worker, target, masterMap)) {
                        uc.handleStation(worker, target, masterMap, null);
                        broadcastCustomizedStates();
                        return;
                    }
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot station worker: Invalid terrain or not enough AP.")));
                    return;
                } else if ("EJECT".equals(req.getActionType()) && u instanceof Worker worker) {
                    UnitController uc = new UnitController();
                    if (uc.canEject(worker)) {
                        uc.handleEject(worker, null);
                        broadcastCustomizedStates();
                        return;
                    }
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot eject worker: No valid adjacent hex found.")));
                    return;
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

                            String reason;
                            if (builder.getCharges() <= 0) {
                                reason = "Builder has no charges left!";
                            } else if (builder.getCurrentAP() < type.getApCost()) {
                                reason = "Not enough AP! Need " + type.getApCost() + ", have " + builder.getCurrentAP();
                            } else if (type != BuildingType.TOWN_HALL && !target.isInsideBorder()) {
                                reason = "Must build inside your territory!";
                            } else if (target.getTerrainType() == TerrainType.MOUNTAIN_RANGE) {
                                reason = "Cannot build on Mountain Range terrain!";
                            } else if (!type.isValidTerrain(target, masterMap)) {
                                reason = type.name() + " cannot be built on " + target.getTerrainType().name() + " terrain!";
                            } else if (!type.hasRequiredTech(masterMap.getPlayerTownHall(clientId))) {
                                reason = "Required technology not researched!";
                            } else {
                                reason = "Not enough resources to build " + type.name() + "!";
                            }
                            server.sendToClient(clientId, gson.toJson(new ErrorResponse(reason)));
                            return;
                        }
                        case "ROAD" -> {
                            if (bc.canBuildRoad(target, builder)) {
                                bc.buildRoad(builder, target, null);
                                broadcastCustomizedStates();
                                return;
                            }
                            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot build road: Need 1 AP, 1 Charge, and valid territory.")));
                            return;
                        }
                        case "WALL" -> {
                            if (bc.canBuildWall(target, req.getDir(), builder)) {
                                bc.buildWall(builder, target, req.getDir(), null);
                                broadcastCustomizedStates();
                                return;
                            }
                            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot build wall: Check AP, charges, resources (10 Wood, 20 Stone), and borders.")));
                            return;
                        }
                        case "DESTROY" -> {
                            if (bc.canDestroy(target, req.getStructureType(), req.getDir(), builder)) {
                                bc.destroyStructure(builder, target, req.getStructureType(), req.getDir(), null);
                                broadcastCustomizedStates();
                                return;
                            }
                            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot destroy structure: Check AP, distance, or validity.")));
                            return;
                        }
                    }
                }
            } finally {
                masterMap.clearActiveTownHall();
            }
        }
        server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid action request or target out of bounds.")));
    }

    public synchronized void handleTrainRequest(String clientId, TrainRequest req) {
        if (!isActivePlayer(clientId)) return;
        masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
        UpgradeController uc = new UpgradeController(masterMap);
        if (uc.canTrainUnit(req.getUnitType())) {
            uc.trainUnit(req.getUnitType(), null);
            broadcastCustomizedStates();
        }
        masterMap.clearActiveTownHall();
    }

    public synchronized void handleCancelProduction(String clientId) {
        if (!isActivePlayer(clientId)) return;
        TownHall th = masterMap.getPlayerTownHall(clientId);
        if (!th.isProductionQueueEmpty()) {
            th.cancelCurrentProduction();
            broadcastCustomizedStates();
        }
    }

    public synchronized void handleCraftItemRequest(String clientId, CraftItemRequest req) {
        if (!isActivePlayer(clientId)) return;
        Hex hex = masterMap.getHexAt(req.getApothecaryQ(), req.getApothecaryR());
        if (hex == null || !(hex.getBuilding() instanceof Apothecary apothecary)) return;
        if (!clientId.equals(hex.getBuilding().getOwnerId())) return;
        if (!apothecary.canQueueItem()) return;

        String itemName = req.getItemName();
        if (!Apothecary.ItemType.isValid(itemName)) return;
        Apothecary.ItemType itemType = Apothecary.ItemType.valueOf(itemName);
        Inventory inv = getPlayerInventory(clientId);
        if (inv == null || !inv.hasEnough(ResourceType.FOOD,  itemType.getFoodCost())
                || !inv.hasEnough(ResourceType.STONE, itemType.getStoneCost())
                || !inv.hasEnough(ResourceType.IRON,  itemType.getIronCost())
                || !inv.hasEnough(ResourceType.WOOD,  itemType.getWoodCost())) return;

        inv.consumeResource(ResourceType.FOOD,  itemType.getFoodCost());
        inv.consumeResource(ResourceType.STONE, itemType.getStoneCost());
        inv.consumeResource(ResourceType.IRON,  itemType.getIronCost());
        inv.consumeResource(ResourceType.WOOD,  itemType.getWoodCost());
        apothecary.queueItem(itemName);
        broadcastCustomizedStates();
    }

    public synchronized void broadcastCustomizedStates() {
        if (players.isEmpty()) return;
        String activePlayerId = players.get(currentPlayerIndex).getId();
        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            Set<String> alliedPlayerIds = getAlliedPlayerIds(clientId);
            String filteredMapJson = fogOfWarFilter.filterForPlayer(masterMap, clientId, alliedPlayerIds);
            GameStateBroadcast update = new GameStateBroadcast(activePlayerId, masterMap.getCurrentTurn(), filteredMapJson);
            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    private void setDiplomaticStatus(String fromId, String toId, String status) {
        diplomacyStates.computeIfAbsent(fromId, k -> new ConcurrentHashMap<>()).put(toId, status);
    }

    private String getDiplomaticStatus(String playerId1, String playerId2) {
        if (playerId1 == null || playerId2 == null) return "Neutral";
        return diplomacyStates.getOrDefault(playerId1, new ConcurrentHashMap<>()).getOrDefault(playerId2, "Neutral");
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
            if ("Allied".equals(entry.getValue())) allies.add(entry.getKey());
        }
        return allies;
    }

    private void sendTradeInboxUpdate(String playerId) {
        List<TradeOffer> inbox = tradeInboxes.getOrDefault(playerId, new ArrayList<>());
        server.sendToClient(playerId, gson.toJson(new TradeInboxBroadcast(new ArrayList<>(inbox))));
    }

    private void removeTradeOffer(TradeOffer offer, String inboxOwnerId) {
        List<TradeOffer> inbox = tradeInboxes.get(inboxOwnerId);
        if (inbox != null) inbox.removeIf(o -> o.getId().equals(offer.getId()));
    }

    private void cancelAllTradesForPlayer(String playerId) {}

    private boolean isActivePlayer(String clientId) {
        if (players.isEmpty()) return false;
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

    private void deliverWarReports(String playerId) {
        List<WarReport> reports = pendingWarReports.get(playerId);
        if (reports == null || reports.isEmpty()) return;
        server.sendToClient(playerId, gson.toJson(new WarReportBroadcast(new ArrayList<>(reports))));
        reports.clear();
    }
}