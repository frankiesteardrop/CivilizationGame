package network.server;

import com.google.gson.Gson;
import controller.CombatController;
import model.*;
import network.messages.game.AttackRequest;
import network.messages.game.DiplomacyRequest;
import network.messages.game.ErrorResponse;
import network.messages.game.GameStateBroadcast;
import network.messages.game.ItemUseRequest;
import network.messages.game.TradeOfferRequest;
import network.messages.game.TradeResponseRequest;
import network.messages.lobby.LobbyPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager {
    private final GameServer server;
    private final Gson gson;
    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates =
            new ConcurrentHashMap<>();

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers) {
        this.server = server;
        this.gson   = new Gson();
        this.players = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
    }

    public synchronized void initializeGame() {
        this.masterMap = new GameMap(20);

        for (LobbyPlayer p1 : players) {
            ConcurrentHashMap<String, String> relations = new ConcurrentHashMap<>();
            for (LobbyPlayer p2 : players) {
                if (!p1.getId().equals(p2.getId())) {
                    relations.put(p2.getId(), "Neutral");
                }
            }
            diplomacyStates.put(p1.getId(), relations);
        }

        System.out.println("✅ [Server] Game Initialized. Transitioning clients to Game UI...");
        broadcastCustomizedStates();
    }

    public synchronized void handleEndTurn(String clientId) {
        String activeId = players.get(currentPlayerIndex).getId();

        if (!activeId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        if (currentPlayerIndex == 0) {
            masterMap.incrementTurn();
        }

        broadcastCustomizedStates();
    }

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
                        && dest.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                        && dest.isExplored()) {
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

    public synchronized void handleAttackRequest(String clientId, AttackRequest req) {
        if (!players.get(currentPlayerIndex).getId().equals(clientId)) {
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

    // ─── Diplomacy Stub (پیاده‌سازی کامل در گام B13) ─────────────────────────

    /**
     * Handler اعلان جنگ و درخواست اتحاد بین بازیکنان.
     * stub — پیاده‌سازی کامل در گام B13.
     */
    public synchronized void handleDiplomacyRequest(String clientId, DiplomacyRequest req) {
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Diplomacy system will be fully implemented in the next step.")));
    }

    // ─── Trade Stubs (پیاده‌سازی کامل در گام B12) ───────────────────────────

    /**
     * Handler دریافت پیشنهاد ترید از یک بازیکن.
     * stub — پیاده‌سازی کامل در گام B12.
     */
    public synchronized void handleTradeOffer(String clientId, TradeOfferRequest req) {
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Trade inbox will be fully implemented in the next step.")));
    }

    /**
     * Handler پاسخ (accept/reject) به یک پیشنهاد ترید.
     * stub — پیاده‌سازی کامل در گام B12.
     */
    public synchronized void handleTradeResponse(String clientId, TradeResponseRequest req) {
        server.sendToClient(clientId, gson.toJson(
                new ErrorResponse("Trade response will be fully implemented in the next step.")));
    }

    // ─── Elimination ──────────────────────────────────────────────────────────

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
            // حذف یونیت‌های بازیکن — از طریق Repository (thread-safe)
            masterMap.getUnits().stream()
                    .filter(u -> playerId.equals(u.getOwnerId()))
                    .forEach(u -> u.takeDamage(u.getMaxHp() + 1));
            masterMap.removeDeadUnits();

            // خراب کردن سازه‌های بازیکن
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
     * فیلتر مپ بر اساس Fog of War.
     * در گام B3 پیاده‌سازی کامل می‌شود. فعلاً مپ اصلی برمی‌گردد.
     */
    private GameMap filterMapForPlayer(GameMap master, String playerId) {
        // TODO (B3): فقط hexهایی که playerId می‌تواند ببیند برگردانده شود
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