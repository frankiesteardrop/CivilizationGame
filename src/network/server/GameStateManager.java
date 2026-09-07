package network.server;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import controller.BuildController;
import controller.CombatController;
import controller.UnitController;
import controller.UpgradeController;
import controller.TribeController;
import controller.TradeController;
import model.*;
import model.CombatResult;
import model.maps.MapDefinition;
import model.maps.PreDesignedMaps;
import network.messages.game.*;
import network.messages.lobby.LobbyPlayer;
import network.util.SharedGsonFactory;

import java.lang.reflect.Type;
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
    private final ConcurrentHashMap<String, List<String>> pendingAllianceRequests = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<TradeOffer>> tradeInboxes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, List<WarReport>> pendingWarReports = new ConcurrentHashMap<>();
    private final Map<String, String> playerNames = new HashMap<>();

    private ServerTurnProcessor serverTurnProcessor;
    private final String selectedMapId;
    private FogOfWarFilter fogOfWarFilter;

    public GameStateManager(
            GameServer server,
            ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers,
            String selectedMapId,
            DatabaseManager databaseManager) {

        this.server = server;
        this.databaseManager = databaseManager;
        this.gson = SharedGsonFactory.createCustomGson();
        this.players = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
        this.selectedMapId = selectedMapId;
    }

    public synchronized void initializeGame() {
        MapDefinition mapDef = PreDesignedMaps.getMap(selectedMapId);

        this.masterMap = new GameMap(
                mapDef.getRadius(),
                mapDef.getRandomSeed()
        );

        masterMap.clearCenterSetup();

        List<int[]> spawnPoints = mapDef.getSpawnPoints();

        for (int i = 0; i < players.size(); i++) {
            if (i >= spawnPoints.size()) {
                break;
            }

            String playerId = players.get(i).getId();
            int[] spawn = spawnPoints.get(i);

            masterMap.placePlayerSpawn(
                    playerId,
                    spawn[0],
                    spawn[1]
            );

            playerNames.put(
                    playerId,
                    players.get(i).getUsername()
            );

            tradeInboxes.put(
                    playerId,
                    new ArrayList<>()
            );

            pendingWarReports.put(
                    playerId,
                    new ArrayList<>()
            );
        }

        for (LobbyPlayer p1 : players) {
            ConcurrentHashMap<String, String> relations =
                    new ConcurrentHashMap<>();

            for (LobbyPlayer p2 : players) {
                if (!p1.getId().equals(p2.getId())) {
                    relations.put(
                            p2.getId(),
                            "Neutral"
                    );
                }
            }

            diplomacyStates.put(
                    p1.getId(),
                    relations
            );
        }

        this.fogOfWarFilter = new FogOfWarFilter(gson);
        this.serverTurnProcessor =
                new ServerTurnProcessor(masterMap);

        server.broadcast(
                gson.toJson(new GameStartBroadcast())
        );

        server.broadcast(
                gson.toJson(new DiplomacyBroadcast(
                        "GAME_START",
                        "server",
                        "Server",
                        "all",
                        "All",
                        "Game Initialized"
                ))
        );

        broadcastCustomizedStates();
    }

    public synchronized boolean loadGameFromJson(String stateJson) {
        try {
            JsonObject root = JsonParser.parseString(stateJson).getAsJsonObject();
            GameMap loadedMap;

            if (root.has("gameData") && !root.get("gameData").isJsonNull()) {
                loadedMap = gson.fromJson(root.get("gameData"), GameMap.class);
            } else {
                loadedMap = gson.fromJson(stateJson, GameMap.class);
            }

            if (loadedMap == null) return false;

            for (TownHall th : loadedMap.getAllPlayerTownHalls()) {
                if (th != null) {
                    Hex thHex = loadedMap.getHexAt(th.getQ(), th.getR());
                    if (thHex != null) thHex.setBuilding(th);
                    for (ProductionCommand cmd : th.getProductionQueue()) {
                        if (cmd != null) cmd.setContextMap(loadedMap);
                    }
                }
            }

            for (Unit unit : loadedMap.getUnits()) {
                if (unit instanceof Worker worker && worker.isStationed()) {
                    Hex workerHex = loadedMap.getHexAt(worker.getQ(), worker.getR());
                    if (workerHex != null && workerHex.getBuilding() != null && !workerHex.getBuilding().isDestroyed()) {
                        worker.restoreStation(workerHex.getBuilding());
                    } else {
                        worker.eject();
                    }
                }
            }

            for (Hex hex : loadedMap.getHexes()) {
                if (!(hex.getBuilding() instanceof TribeCamp camp)) continue;
                camp.getTribe().postLoad();
                model.mission.Mission m = camp.getTribe().getMission();
                if (m != null) m.setGoal(camp.getTribe().getType().getMissionGoal());
            }

            this.masterMap = loadedMap;

            if (root.has("currentPlayerIndex")) {
                this.currentPlayerIndex = root.get("currentPlayerIndex").getAsInt();
            }

            if (root.has("diplomacyStates")) {
                Type type = new TypeToken<ConcurrentHashMap<String, ConcurrentHashMap<String, String>>>(){}.getType();
                ConcurrentHashMap<String, ConcurrentHashMap<String, String>> loaded = gson.fromJson(root.get("diplomacyStates"), type);
                if (loaded != null) {
                    this.diplomacyStates.clear();
                    this.diplomacyStates.putAll(loaded);
                }
            }

            if (root.has("pendingAllianceRequests")) {
                Type type = new TypeToken<ConcurrentHashMap<String, List<String>>>(){}.getType();
                ConcurrentHashMap<String, List<String>> loaded = gson.fromJson(root.get("pendingAllianceRequests"), type);
                if (loaded != null) {
                    this.pendingAllianceRequests.clear();
                    this.pendingAllianceRequests.putAll(loaded);
                }
            }

            if (root.has("tradeInboxes")) {
                Type type = new TypeToken<ConcurrentHashMap<String, List<TradeOffer>>>(){}.getType();
                ConcurrentHashMap<String, List<TradeOffer>> loaded = gson.fromJson(root.get("tradeInboxes"), type);
                if (loaded != null) {
                    this.tradeInboxes.clear();
                    this.tradeInboxes.putAll(loaded);
                }
            }

            if (root.has("pendingWarReports")) {
                Type type = new TypeToken<ConcurrentHashMap<String, List<WarReport>>>(){}.getType();
                ConcurrentHashMap<String, List<WarReport>> loaded = gson.fromJson(root.get("pendingWarReports"), type);
                if (loaded != null) {
                    this.pendingWarReports.clear();
                    this.pendingWarReports.putAll(loaded);
                }
            }

            if (root.has("playerNames")) {
                Type type = new TypeToken<Map<String, String>>(){}.getType();
                Map<String, String> loaded = gson.fromJson(root.get("playerNames"), type);
                if (loaded != null) {
                    this.playerNames.clear();
                    this.playerNames.putAll(loaded);
                }
            } else {
                this.playerNames.clear();
                for (LobbyPlayer p : players) {
                    this.playerNames.put(p.getId(), p.getUsername());
                }
            }

            this.fogOfWarFilter = new FogOfWarFilter(gson);
            this.serverTurnProcessor = new ServerTurnProcessor(masterMap);

            server.broadcast(gson.toJson(new GameStartBroadcast()));
            server.broadcast(gson.toJson(new DiplomacyBroadcast("GAME_START", "server", "Server", "all", "All", "Game Loaded from Server")));

            broadcastCustomizedStates();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public synchronized void saveGameToJson(String slot) {
        JsonObject wrapper = new JsonObject();

        wrapper.addProperty("saveVersion", "3.0");
        wrapper.addProperty("slotName", slot);
        wrapper.addProperty("turnNumber", masterMap.getCurrentTurn());
        wrapper.addProperty("season", masterMap.getCurrentSeason().name());
        wrapper.addProperty("thLevel", masterMap.getTownHall().getLevel());
        wrapper.addProperty("saveTime", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        wrapper.addProperty("gameSummary", "Multiplayer Match");

        wrapper.addProperty("currentPlayerIndex", currentPlayerIndex);
        wrapper.add("diplomacyStates", gson.toJsonTree(diplomacyStates));
        wrapper.add("pendingAllianceRequests", gson.toJsonTree(pendingAllianceRequests));
        wrapper.add("tradeInboxes", gson.toJsonTree(tradeInboxes));
        wrapper.add("pendingWarReports", gson.toJsonTree(pendingWarReports));
        wrapper.add("playerNames", gson.toJsonTree(playerNames));

        wrapper.add("gameData", gson.toJsonTree(masterMap));

        databaseManager.saveGameSession(slot, wrapper.toString());
    }

    public synchronized void handleEndTurn(String clientId) {
        if (!isActivePlayer(clientId)) return;
        serverTurnProcessor.processPlayerTurnEnd(masterMap, clientId);
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
        boolean isNewRound = currentPlayerIndex == 0;

        if (isNewRound) {
            masterMap.incrementTurn();
            serverTurnProcessor.processGlobalRoundEnd(masterMap);
        }

        saveGameToJson("autosave");
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

        for (List<String> list : pendingAllianceRequests.values()) {
            list.remove(playerId);
        }
        pendingAllianceRequests.remove(playerId);

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
            boolean isNewRound = currentPlayerIndex == 0;
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
            if (players.get(i).getId().equals(playerId)) {
                removedIndex = i; break;
            }
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

    private void cancelAllTradesForPlayer(String playerId) {
        Inventory leavingPlayerInv = getPlayerInventory(playerId);

        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            String targetId = entry.getKey();
            List<TradeOffer> inbox = entry.getValue();

            Iterator<TradeOffer> iterator = inbox.iterator();
            while (iterator.hasNext()) {
                TradeOffer offer = iterator.next();

                if (offer.getOffererId().equals(playerId)) {
                    iterator.remove();
                    if (leavingPlayerInv != null) {
                        leavingPlayerInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
                    }
                    sendTradeInboxUpdate(targetId);
                }
                else if (targetId.equals(playerId)) {
                    iterator.remove();
                    Inventory offererInv = getPlayerInventory(offer.getOffererId());
                    if (offererInv != null) {
                        offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
                    }
                    server.sendToClient(offer.getOffererId(), gson.toJson(new GameNotificationMessage("🚫 Trade canceled automatically because the target player left the game.")));
                }
            }
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
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn.")));
            return;
        }
        if (req == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack request is missing.")));
            return;
        }

        List<String> attackerIds = req.getAttackerIds();
        if (attackerIds == null || attackerIds.isEmpty()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No attacker IDs provided.")));
            return;
        }

        Unit firstAttacker = masterMap.getUnits().stream()
                .filter(u -> u.isAlive() && attackerIds.contains(u.getId()) && clientId.equals(u.getOwnerId()))
                .findFirst().orElse(null);

        if (firstAttacker == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No valid owned attacker found.")));
            return;
        }

        Hex sourceHex = masterMap.getHexAt(firstAttacker.getQ(), firstAttacker.getR());
        Hex targetHex = masterMap.getHexAt(req.getTargetQ(), req.getTargetR());

        if (sourceHex == null || targetHex == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid attack coordinates.")));
            return;
        }

        int distance = masterMap.getHexDistance(sourceHex.getQ(), sourceHex.getR(), targetHex.getQ(), targetHex.getR());
        if (distance < 1 || distance > 2) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack rejected: target is outside the valid attack range.")));
            return;
        }

        List<Unit> ownedUnitsOnSource = masterMap.getUnits().stream()
                .filter(u -> u.isAlive()
                        && attackerIds.contains(u.getId())
                        && u.getQ() == sourceHex.getQ()
                        && u.getR() == sourceHex.getR()
                        && clientId.equals(u.getOwnerId()))
                .toList();

        if (ownedUnitsOnSource.isEmpty()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No owned units are available on the source hex.")));
            return;
        }

        List<Unit> militaryUnits = ownedUnitsOnSource.stream().filter(this::isMilitaryUnit).toList();
        if (militaryUnits.isEmpty()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No owned military units are available on the source hex.")));
            return;
        }

        List<Unit> validAttackers = militaryUnits.stream().filter(u -> isValidAttackerForDistance(u, distance)).toList();

        if (validAttackers.isEmpty()) {
            if (distance == 2) server.sendToClient(clientId, gson.toJson(new ErrorResponse("Range-2 attacks require an Archer or Catapult.")));
            else server.sendToClient(clientId, gson.toJson(new ErrorResponse("No owned military unit on the source hex can attack this target.")));
            return;
        }

        Unit exhaustedAttacker = validAttackers.stream().filter(u -> u.getCurrentAP() < 1).findFirst().orElse(null);
        if (exhaustedAttacker != null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack rejected: " + exhaustedAttacker.getType().name() + " has no AP remaining.")));
            return;
        }

        String targetOwnerId = getTargetOwnerId(targetHex);
        String attackPermissionError = validateAttackPermission(clientId, targetOwnerId);

        if (attackPermissionError != null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse(attackPermissionError)));
            return;
        }

        long aliveBefore = 0;
        if (targetOwnerId != null) {
            aliveBefore = masterMap.getUnits().stream().filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId())).count();
        }

        CombatController cc = new CombatController(masterMap);

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

        CombatResult result = cc.executeAttack(validAttackers, sourceHex, targetHex, isSiegeAttack, isTargetAnimal, targetHasWall);
        if (result == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack rejected by server combat validation.")));
            return;
        }

        if (targetOwnerId != null && isSiegeAttack) {
            checkPlayerElimination(targetOwnerId);
        }

        if (targetOwnerId != null && !targetOwnerId.equals(clientId)) {
            long aliveAfter = masterMap.getUnits().stream().filter(u -> u.isAlive() && targetOwnerId.equals(u.getOwnerId())).count();
            int unitsLost = (int) (aliveBefore - aliveAfter);

            WarReport report = new WarReport(
                    clientId, playerNames.getOrDefault(clientId, clientId),
                    targetHex.getQ(), targetHex.getR(),
                    unitsLost, result.getAttackerUnitsDestroyed(),
                    isSiegeAttack ? result.getSiegeDamage() : 0,
                    isSiegeAttack, result.getAttackerDice(), result.getDefenderDice()
            );
            pendingWarReports.computeIfAbsent(targetOwnerId, k -> new ArrayList<>()).add(report);
        }
        broadcastCustomizedStates();
    }

    public synchronized void handleCaptureHexRequest(String clientId, CaptureHexRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn.")));
            return;
        }
        if (req == null || req.getUnitId() == null || req.getUnitId().isBlank()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid capture request: unit ID is missing.")));
            return;
        }

        Unit unit = masterMap.getUnits().stream()
                .filter(u -> u.isAlive() && req.getUnitId().equals(u.getId()))
                .findFirst().orElse(null);

        if (unit == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: unit does not exist or is no longer alive.")));
            return;
        }
        if (!clientId.equals(unit.getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unauthorized: you do not own this unit.")));
            return;
        }
        if (!isCaptureUnit(unit)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Only Swordsman, Archer, or Cavalry units can capture a hex.")));
            return;
        }
        if (unit.getCurrentAP() < 1) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: the unit has no AP remaining.")));
            return;
        }

        Hex targetHex = masterMap.getHexAt(req.getTargetQ(), req.getTargetR());
        if (targetHex == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: target hex is invalid.")));
            return;
        }

        int distance = masterMap.getHexDistance(unit.getQ(), unit.getR(), targetHex.getQ(), targetHex.getR());
        if (distance != 1) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: the target hex must be adjacent to the unit.")));
            return;
        }

        if (targetHex.getBuilding() instanceof TribeCamp) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: a Tribe Camp cannot be captured this way.")));
            return;
        }
        if (targetHex.isInsideBorder()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: this hex is already inside your controlled territory.")));
            return;
        }
        if (hasHostileEntityAt(targetHex, clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: the target hex is occupied by a hostile unit or structure.")));
            return;
        }
        if (!isCaptureHexNearEnemyTribeCamp(targetHex)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: this hex is not adjacent to an enemy Tribe Camp.")));
            return;
        }

        if (!unit.consumeAP(1)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Capture rejected: failed to consume the required AP.")));
            return;
        }

        targetHex.setInsideBorder(true);
        targetHex.setExplored(true);
        masterMap.updateFogOfWar();
        GameEventDispatcher.fireBorderExpanded(targetHex.getQ(), targetHex.getR());
        server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("🏴 Hex captured successfully. 1 AP consumed.")));
        broadcastCustomizedStates();
    }

    private boolean isMilitaryUnit(Unit unit) {
        if (unit == null) return false;
        return switch (unit.getType()) {
            case SWORDSMAN, ARCHER, CAVALRY, CATAPULT -> true;
            default -> false;
        };
    }

    private boolean isCaptureUnit(Unit unit) {
        if (unit == null) return false;
        return switch (unit.getType()) {
            case SWORDSMAN, ARCHER, CAVALRY -> true;
            default -> false;
        };
    }

    private boolean isValidAttackerForDistance(Unit unit, int distance) {
        if (!isMilitaryUnit(unit)) return false;
        if (!unit.isAlive()) return false;
        if (unit.getAttackRange() < distance) return false;
        if (distance == 2) return unit.getType() == UnitType.ARCHER || unit.getType() == UnitType.CATAPULT;
        return distance == 1;
    }

    private boolean hasHostileEntityAt(Hex targetHex, String clientId) {
        for (Unit unit : masterMap.getUnits()) {
            if (!unit.isAlive()) continue;
            if (unit.getQ() != targetHex.getQ() || unit.getR() != targetHex.getR()) continue;
            if (unit.getType() == UnitType.BEAR) return true;
            String ownerId = unit.getOwnerId();
            if (ownerId != null && !clientId.equals(ownerId)) return true;
        }

        Building building = targetHex.getBuilding();
        if (building != null && !building.isDestroyed()) {
            String ownerId = building.getOwnerId();
            if (ownerId != null && !clientId.equals(ownerId)) return true;
        }
        return false;
    }

    private boolean isCaptureHexNearEnemyTribeCamp(Hex targetHex) {
        for (int i = 0; i < 6; i++) {
            Hex neighbor = masterMap.getNeighbor(targetHex, i);
            if (neighbor == null) continue;
            if (!(neighbor.getBuilding() instanceof TribeCamp camp)) continue;
            if (camp.isDestroyed()) continue;
            if ("Enemy".equals(camp.getTribe().getState().getName())) return true;
        }
        return false;
    }

    private String validateAttackPermission(String attackerId, String targetOwnerId) {
        if (targetOwnerId == null) return null;
        boolean targetExists = players.stream().anyMatch(player -> targetOwnerId.equals(player.getId()));
        if (!targetExists) return "Attack rejected: target belongs to an invalid or inactive player.";
        if (attackerId.equals(targetOwnerId)) return "You cannot attack your own units or structures.";

        String diplomaticStatus = getDiplomaticStatus(attackerId, targetOwnerId);
        if ("Enemy".equals(diplomaticStatus)) return null;
        if ("Allied".equals(diplomaticStatus)) return "You cannot attack an allied player.";
        if ("Neutral".equals(diplomaticStatus)) return "You cannot attack this player because your diplomatic status is Neutral. Declare war first.";

        return "You cannot attack this player because the current diplomatic status does not allow PvP combat.";
    }

    public synchronized void handleTribeActionRequest(String clientId, TribeActionRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        if (req.getAmount() < 0) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Amount cannot be negative.")));
            return;
        }

        Hex hex = masterMap.getHexAt(req.getCampQ(), req.getCampR());
        if (hex == null || !(hex.getBuilding() instanceof TribeCamp camp) || camp.isDestroyed()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid Tribe Camp.")));
            return;
        }

        masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
        TribeController tc = new TribeController(masterMap);

        boolean success = false;
        switch (req.getAction()) {
            case "ACCEPT_MISSION" -> { tc.acceptMission(camp); success = true; }
            case "CANCEL_MISSION" -> { tc.cancelMission(camp); success = true; }
            case "DELIVER_MISSION" -> success = tc.deliverMission(camp);
            case "FORM_ALLIANCE" -> success = tc.formAlliance(camp);
            case "SEND_GIFT" -> success = tc.sendGift(camp, req.getResourceType(), req.getAmount());
            case "DECLARE_WAR" -> { tc.declareWar(camp); success = true; }
            case "REQUEST_PEACE" -> success = tc.requestPeace(camp);
            case "TRADE" -> success = tc.tradeWithTribe(camp, req.getResourceType(), req.getAmount(), req.getGetResourceType());
        }

        masterMap.clearActiveTownHall();
        if (!success) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Tribe action failed (check resources or conditions).")));
        }
        broadcastCustomizedStates();
    }

    public synchronized void handleNpcTradeRequest(String clientId, NpcTradeRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        if (req.getAmountToGive() < 0) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade amount cannot be negative.")));
            return;
        }

        Hex hex = masterMap.getHexAt(req.getHexQ(), req.getHexR());
        if (hex == null || hex.getBuilding() == null || hex.getBuilding().isDestroyed()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid building.")));
            return;
        }

        masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
        TradeController tc = new TradeController(masterMap);

        boolean success = false;
        if ("BAZAAR".equals(req.getBuildingType()) && hex.getBuilding() instanceof Bazaar b) {
            if (!clientId.equals(b.getOwnerId())) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not own this Bazaar.")));
            } else {
                success = tc.tradeWithBazaar(b, req.getGiveType(), req.getGetType());
            }
        } else if ("TRADING_POST".equals(req.getBuildingType()) && hex.getBuilding() instanceof TradingPost p) {
            success = tc.tradeWithTradingPost(p, hex, req.getGiveType(), req.getAmountToGive(), req.getGetType());
        }

        masterMap.clearActiveTownHall();
        if (!success) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("NPC Trade failed (already traded or not enough resources).")));
        }
        broadcastCustomizedStates();
    }

    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        if (req.getOfferAmount() <= 0 || req.getRequestAmount() <= 0) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade amounts must be greater than zero.")));
            return;
        }

        String targetId = req.getTargetPlayerId();
        if (targetId == null || targetId.equals(clientId) || !isValidTarget(clientId, targetId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid trade target.")));
            return;
        }

        Inventory offererInv = getPlayerInventory(clientId);
        if (offererInv == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You must build a Town Hall before trading!")));
            return;
        }

        if (!offererInv.hasEnough(req.getOfferType(), req.getOfferAmount())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not have enough resources.")));
            return;
        }

        offererInv.lockResource(req.getOfferType(), req.getOfferAmount());
        TradeOffer offer = new TradeOffer(clientId, playerNames.get(clientId), targetId,
                req.getOfferType(), req.getOfferAmount(), req.getRequestType(), req.getRequestAmount());
        tradeInboxes.computeIfAbsent(targetId, k -> new ArrayList<>()).add(offer);
        sendTradeInboxUpdate(targetId);
        broadcastCustomizedStates();
    }

    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn! You can only interact with your Trade Inbox during your turn.")));
            return;
        }

        TradeOffer offer = null;
        String inboxOwnerId = null;

        for (Map.Entry<String, List<TradeOffer>> entry : tradeInboxes.entrySet()) {
            for (TradeOffer o : entry.getValue()) {
                if (o.getId().equals(req.getTradeId())) {
                    offer = o;
                    inboxOwnerId = entry.getKey();
                    break;
                }
            }
            if (offer != null) break;
        }

        if (offer == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Trade offer not found or already processed.")));
            return;
        }

        if (!clientId.equals(offer.getTargetId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Exploit attempt detected: You are not the target of this trade.")));
            return;
        }

        Inventory targetInv = getPlayerInventory(clientId);
        Inventory offererInv = getPlayerInventory(offer.getOffererId());

        if (req.isAccepted()) {
            if (targetInv == null) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You need a Town Hall to accept trades.")));
                return;
            }
            if (!targetInv.hasEnough(offer.getRequestType(), offer.getRequestAmount())) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You don't have enough resources to accept this trade anymore!")));
                return;
            }
        }

        if (req.isAccepted() && targetInv != null && offererInv != null) {
            targetInv.consumeResource(offer.getRequestType(), offer.getRequestAmount());
            offererInv.consumeLockedResource(offer.getOfferType(), offer.getOfferAmount());
            targetInv.addResource(offer.getOfferType(), offer.getOfferAmount());
            offererInv.addResource(offer.getRequestType(), offer.getRequestAmount());
        } else {
            if (offererInv != null) {
                offererInv.unlockResource(offer.getOfferType(), offer.getOfferAmount());
            }
        }

        removeTradeOffer(offer, inboxOwnerId);
        sendTradeInboxUpdate(clientId);
        broadcastCustomizedStates();
    }

    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        String targetId = req.getTargetPlayerId();
        if (!isValidTarget(clientId, targetId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid diplomacy target.")));
            return;
        }

        if ("DECLARE_WAR".equals(req.getAction())) {
            if (!isActivePlayer(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn to declare war!")));
                return;
            }
            setDiplomaticStatus(clientId, targetId, "Enemy");
            setDiplomaticStatus(targetId, clientId, "Enemy");

            if (pendingAllianceRequests.containsKey(clientId)) pendingAllianceRequests.get(clientId).remove(targetId);
            if (pendingAllianceRequests.containsKey(targetId)) pendingAllianceRequests.get(targetId).remove(clientId);

            server.broadcast(gson.toJson(new DiplomacyBroadcast("WAR_DECLARED", clientId, playerNames.get(clientId), targetId, playerNames.get(targetId), "War declared!")));
            broadcastCustomizedStates();

        } else if ("REQUEST_ALLIANCE".equals(req.getAction())) {
            if (!isActivePlayer(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
                return;
            }
            String currentStatus = getDiplomaticStatus(clientId, targetId);
            if ("Enemy".equals(currentStatus) || "Allied".equals(currentStatus)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot request alliance in your current diplomatic state.")));
                return;
            }

            pendingAllianceRequests.computeIfAbsent(targetId, k -> new ArrayList<>()).add(clientId);

            server.sendToClient(targetId, gson.toJson(new DiplomacyBroadcast("ALLIANCE_REQUESTED", clientId, playerNames.get(clientId), targetId, playerNames.get(targetId), playerNames.get(clientId) + " requested an alliance.")));
            server.sendToClient(clientId, gson.toJson(new GameNotificationMessage("Alliance request sent to " + playerNames.get(targetId) + ".")));

        } else if ("BREAK_ALLIANCE".equals(req.getAction())) {
            if (!isActivePlayer(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
                return;
            }
            if ("Allied".equals(getDiplomaticStatus(clientId, targetId))) {
                setDiplomaticStatus(clientId, targetId, "Neutral");
                setDiplomaticStatus(targetId, clientId, "Neutral");
                server.broadcast(gson.toJson(new DiplomacyBroadcast("ALLIANCE_BROKEN", clientId, playerNames.get(clientId), targetId, playerNames.get(targetId), playerNames.get(clientId) + " broke the alliance with " + playerNames.get(targetId) + ".")));
                broadcastCustomizedStates();
            } else {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You are not allied with this player.")));
            }
        }
    }

    public synchronized void handleAllianceResponse(String clientId, AllianceResponseRequest req) {
        String requesterId = req.getRequesterId();

        List<String> requestsForMe = pendingAllianceRequests.get(clientId);
        if (requestsForMe == null || !requestsForMe.contains(requesterId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No pending alliance request from this player.")));
            return;
        }

        if ("Enemy".equals(getDiplomaticStatus(clientId, requesterId))) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Alliance rejected: You are currently at war with this player.")));
            requestsForMe.remove(requesterId);
            return;
        }

        if (req.isAccepted()) {
            setDiplomaticStatus(clientId, requesterId, "Allied");
            setDiplomaticStatus(requesterId, clientId, "Allied");
            server.broadcast(gson.toJson(new DiplomacyBroadcast("ALLIANCE_FORMED", requesterId, playerNames.get(requesterId), clientId, playerNames.get(clientId), playerNames.get(requesterId) + " and " + playerNames.get(clientId) + " have formed an alliance!")));
            broadcastCustomizedStates();
        } else {
            server.sendToClient(requesterId, gson.toJson(new DiplomacyBroadcast("ALLIANCE_REJECTED", clientId, playerNames.get(clientId), requesterId, playerNames.get(requesterId), playerNames.get(clientId) + " rejected your alliance request.")));
        }

        requestsForMe.remove(requesterId);
    }

    public synchronized void handleItemUseRequest(String clientId, ItemUseRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Unit target = masterMap.getUnits().stream()
                .filter(u -> u.isAlive() && u.getId().equals(req.getUnitId()))
                .findFirst().orElse(null);

        if (target == null || !clientId.equals(target.getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("This unit does not belong to you or is dead.")));
            return;
        }
        if (target.hasUsedItemThisTurn()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unit has already used an item this turn.")));
            return;
        }

        Inventory playerInv = getPlayerInventory(clientId);
        if (playerInv == null || !playerInv.hasItem(req.getItemName())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not have this item.")));
            return;
        }

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
            if (!dest.isVisible(clientId)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot teleport into the Fog of War. Only currently visible hexes are valid.")));
                return;
            }
            if (dest.getTerrainType() == TerrainType.MOUNTAIN_RANGE || dest.getTerrainType() == TerrainType.SEA) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Terrain is impassable.")));
                return;
            }
        }

        playerInv.consumeItem(req.getItemName());
        target.setUsedItemThisTurn(true);

        switch (req.getItemName()) {
            case "TELEPORT" -> {
                target.moveTo(req.getDestQ(), req.getDestR(), 0);
                masterMap.updateFogOfWar();
            }
            case "MOBILITY" -> target.addTemporaryAP(2);
            case "COMBAT" -> {
                target.setTemporaryCombatDiceBonus(1);
                target.setTemporarySiegeBonus(5);
            }
            default -> {
                playerInv.addItem(req.getItemName(), 1);
                target.setUsedItemThisTurn(false);
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unknown item type: " + req.getItemName())));
                return;
            }
        }
        broadcastCustomizedStates();
    }

    public synchronized void handleMoveRequest(String clientId, MoveRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        Unit unit = masterMap.getUnits().stream()
                .filter(u -> u.isAlive() && u.getId().equals(req.getUnitId()))
                .findFirst().orElse(null);

        if (unit == null || !clientId.equals(unit.getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Unit not found or does not belong to you.")));
            return;
        }

        Hex target = masterMap.getHexAt(req.getTgtQ(), req.getTgtR());
        if (target == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Target hex is invalid.")));
            return;
        }

        UnitController uc = new UnitController();
        if (uc.canMove(unit, target, masterMap)) {
            uc.executeMove(unit, target, masterMap);
            broadcastCustomizedStates();
        } else {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid move. Check AP, terrain, and unit caps.")));
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
                if ("EXPAND".equals(req.getActionType())) {
                    if (u instanceof BorderExpander expander) {
                        UnitController uc = new UnitController();
                        if (uc.handleExpandBorder(expander, masterMap)) {
                            broadcastCustomizedStates();
                        } else {
                            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot expand border: check AP, contiguous border, and explored hex.")));
                        }
                        return;
                    } else {
                        server.sendToClient(clientId, gson.toJson(new ErrorResponse("No valid BorderExpander found at specified position.")));
                        return;
                    }
                } else if ("STATION".equals(req.getActionType()) && u instanceof Worker worker) {
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
                            if (builder.getCharges() <= 0) reason = "Builder has no charges left!";
                            else if (builder.getCurrentAP() < type.getApCost()) reason = "Not enough AP! Need " + type.getApCost() + ", have " + builder.getCurrentAP();
                            else if (type != BuildingType.TOWN_HALL && !target.isInsideBorder()) reason = "Must build inside your territory!";
                            else if (target.getTerrainType() == TerrainType.MOUNTAIN_RANGE) reason = "Cannot build on Mountain Range terrain!";
                            else if (!type.isValidTerrain(target, masterMap)) reason = type.name() + " cannot be built on " + target.getTerrainType().name() + " terrain!";
                            else if (!type.hasRequiredTech(masterMap.getPlayerTownHall(clientId))) reason = "Required technology not researched!";
                            else reason = "Not enough resources to build " + type.name() + "!";
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
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        masterMap.setActiveTownHall(masterMap.getPlayerTownHall(clientId));
        UpgradeController uc = new UpgradeController(masterMap);
        String unitType = req.getUnitType();

        try {
            if ("UPGRADE_TH".equals(unitType)) {
                if (uc.canAffordWarehouseUpgrade()) {
                    uc.handleWarehouseUpgrade(null);
                    broadcastCustomizedStates();
                } else {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot upgrade Town Hall: check resources and production queue.")));
                }
            } else if (unitType != null && unitType.startsWith("TECH:")) {
                String techId = unitType.substring(5);
                if (uc.canUnlockTech(techId)) {
                    uc.unlockTech(techId, null);
                    broadcastCustomizedStates();
                } else {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot unlock tech: check prerequisites, level, and resources.")));
                }
            } else {
                if (uc.canTrainUnit(unitType)) {
                    uc.trainUnit(unitType, null);
                    broadcastCustomizedStates();
                } else {
                    server.sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot train unit: check resources, unit caps, or queue status.")));
                }
            }
        } finally {
            masterMap.clearActiveTownHall();
        }
    }

    public synchronized void handleCancelProduction(String clientId) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        TownHall th = masterMap.getPlayerTownHall(clientId);
        if (th != null && !th.isProductionQueueEmpty()) {
            th.cancelCurrentProduction();
            broadcastCustomizedStates();
        } else {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("No active production to cancel.")));
        }
    }

    public synchronized void handleCraftItemRequest(String clientId, CraftItemRequest req) {
        if (!isActivePlayer(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }
        Hex hex = masterMap.getHexAt(req.getApothecaryQ(), req.getApothecaryR());
        if (hex == null || !(hex.getBuilding() instanceof Apothecary apothecary) || apothecary.isDestroyed()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid Apothecary.")));
            return;
        }
        if (!clientId.equals(hex.getBuilding().getOwnerId())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You do not own this Apothecary.")));
            return;
        }
        if (!apothecary.canQueueItem()) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Apothecary is already busy.")));
            return;
        }

        String itemName = req.getItemName();
        if (!Apothecary.ItemType.isValid(itemName)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid item type.")));
            return;
        }
        Apothecary.ItemType itemType = Apothecary.ItemType.valueOf(itemName);

        Inventory inv = getPlayerInventory(clientId);
        if (inv == null) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You need a Town Hall to craft items.")));
            return;
        }

        if (!inv.hasEnough(ResourceType.FOOD, itemType.getFoodCost())
                || !inv.hasEnough(ResourceType.STONE, itemType.getStoneCost())
                || !inv.hasEnough(ResourceType.IRON, itemType.getIronCost())
                || !inv.hasEnough(ResourceType.WOOD, itemType.getWoodCost())) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Not enough resources to craft this item.")));
            return;
        }

        inv.consumeResource(ResourceType.FOOD, itemType.getFoodCost());
        inv.consumeResource(ResourceType.STONE, itemType.getStoneCost());
        inv.consumeResource(ResourceType.IRON, itemType.getIronCost());
        inv.consumeResource(ResourceType.WOOD, itemType.getWoodCost());
        apothecary.queueItem(itemName);
        broadcastCustomizedStates();
    }

    public synchronized void broadcastCustomizedStates() {
        if (players.isEmpty()) return;
        String activePlayerId = players.get(currentPlayerIndex).getId();
        String activePlayerName = playerNames.getOrDefault(activePlayerId, "Unknown");

        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            Set<String> alliedPlayerIds = getAlliedPlayerIds(clientId);
            String filteredMapJson = fogOfWarFilter.filterForPlayer(masterMap, clientId, alliedPlayerIds);

            Map<String, String[]> specificDiplomacy = new HashMap<>();
            ConcurrentHashMap<String, String> relations = diplomacyStates.get(clientId);
            if (relations != null) {
                for (Map.Entry<String, String> entry : relations.entrySet()) {
                    String otherId = entry.getKey();
                    String status = entry.getValue();
                    String otherName = playerNames.getOrDefault(otherId, "Unknown");
                    specificDiplomacy.put(otherId, new String[]{otherName, status});
                }
            }

            GameStateBroadcast update = new GameStateBroadcast(
                    activePlayerId, activePlayerName, masterMap.getCurrentTurn(), filteredMapJson, specificDiplomacy
            );
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
        if (inbox != null) {
            inbox.removeIf(o -> o.getId().equals(offer.getId()));
        }
    }

    private boolean isActivePlayer(String clientId) {
        if (players.isEmpty()) return false;
        return players.get(currentPlayerIndex).getId().equals(clientId);
    }

    private Inventory getPlayerInventory(String ownerId) {
        Empire emp = masterMap.getEmpire(ownerId);
        if (emp != null) return emp.getInventory();

        for (Hex h : masterMap.getHexes()) {
            if (h.getBuilding() != null && h.getBuilding().getType() == BuildingType.TOWN_HALL
                    && ownerId.equals(h.getBuilding().getOwnerId())) {
                return ((TownHall) h.getBuilding()).getInventory();
            }
        }
        return null;
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