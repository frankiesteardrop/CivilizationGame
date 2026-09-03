package network.server;

import com.google.gson.Gson;
import controller.CombatController;
import model.GameMap;
import model.Hex;
import model.Unit;
import model.UnitType;
import network.messages.game.AttackRequest;
import network.messages.game.ErrorResponse;
import network.messages.game.GameStateBroadcast;
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

    // نگهداری وضعیت دیپلماتیک (Neutral, Enemy, Allied) برای سیستم PvP
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, String>> diplomacyStates = new ConcurrentHashMap<>();

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers) {
        this.server = server;
        this.gson = new Gson();
        this.players = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
    }

    public synchronized void initializeGame() {
        this.masterMap = new GameMap(20);

        // مقداردهی اولیه دیپلماسی: همه بازیکنان در ابتدا نسبت به هم Neutral هستند
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

    // --- منطق یکپارچه و امنیتی حمله PvP ---
    public synchronized void handleAttackRequest(String clientId, AttackRequest req) {
        // ۱. بررسی نوبت
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

        // ۲. استخراج مهاجمین و تایید مطلق مالکیت (جلوگیری از کنترل نیروی حریف)
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

        // ۳. استخراج مالک هدف و اعتبارسنجی دیپلماسی
        String targetOwnerId = getTargetOwnerId(targetHex);

        if (targetOwnerId != null && targetOwnerId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("You cannot attack your own units or structures!")));
            return;
        }

        if (targetOwnerId != null) {
            String diploStatus = getDiplomaticStatus(clientId, targetOwnerId);
            if (!"Enemy".equals(diploStatus)) {
                server.sendToClient(clientId, gson.toJson(new ErrorResponse("You must declare war first! Status: " + diploStatus)));
                return;
            }
        }

        // ۴. پیکربندی پارامترهای نبرد
        boolean isTargetAnimal = masterMap.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getType() == UnitType.BEAR && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR());

        boolean hasEnemyUnit = masterMap.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && !clientId.equals(u.getOwnerId()));

        boolean isSiegeAttack = !hasEnemyUnit && !isTargetAnimal;

        boolean targetHasWall = false;
        for (int i = 0; i < 6; i++) {
            if (masterMap.getNeighbor(sourceHex, i) == targetHex) {
                targetHasWall = sourceHex.hasWall(i);
                break;
            }
        }

        // ۵. اجرای نبرد با هسته اصلی (CombatController)
        CombatController cc = new CombatController(masterMap);
        int result = cc.executeAttack(attackers, sourceHex, targetHex, isSiegeAttack, isTargetAnimal, targetHasWall);

        if (result == -1) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("Attack failed. Check AP or attack range.")));
            return;
        }

        // ۶. همگام‌سازی نتایج برای تمام کلاینت‌ها
        broadcastCustomizedStates();
    }

    private String getTargetOwnerId(Hex targetHex) {
        for (Unit u : masterMap.getUnits()) {
            if (u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()) {
                if (u.getOwnerId() != null) return u.getOwnerId();
            }
        }
        if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
            return targetHex.getBuilding().getOwnerId();
        }
        return null;
    }

    private String getDiplomaticStatus(String attackerId, String defenderId) {
        if (attackerId == null || defenderId == null) return "Neutral";
        return diplomacyStates.get(attackerId).getOrDefault(defenderId, "Neutral");
    }

    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            GameMap playerSpecificMap = filterMapForPlayer(masterMap, clientId);

            String mapJson = gson.toJson(playerSpecificMap);
            GameStateBroadcast update = new GameStateBroadcast(activePlayerId, masterMap.getCurrentTurn(), mapJson);

            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    private GameMap filterMapForPlayer(GameMap master, String playerId) {
        return master;
    }
}