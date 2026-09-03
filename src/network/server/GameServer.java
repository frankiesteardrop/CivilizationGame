package network.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.messages.game.*;
import network.messages.lobby.*;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * هسته مرکزی سرور بازی.
 *
 * <p>مسئولیت‌ها (Single Responsibility در سطح کلاس):
 * <ul>
 *   <li>قبول اتصالات جدید — یک thread اختصاصی per connection</li>
 *   <li>مسیردهی (routing) پیام‌های JSON ورودی به LobbyManager یا GameStateManager</li>
 *   <li>ارسال پیام به یک یا همه کلاینت‌ها (broadcast / sendToClient)</li>
 * </ul>
 *
 * <p>منطق لابی در LobbyManager و منطق بازی در GameStateManager قرار دارند.
 * GameServer هیچ منطق بازی مستقیمی ندارد.
 */
public class GameServer {

    private static final int PORT = 8080;

    /** نگهداری Thread-safe از همه اتصالات فعال */
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    /** مدیریت وضعیت لابی — از همان ابتدا موجود است */
    private final LobbyManager lobbyManager = new LobbyManager(this);

    /**
     * مدیریت وضعیت بازی — null است تا زمانی که هاست "Start Game" را فشار دهد.
     * volatile برای دیده شدن صحیح توسط همه thread‌ها.
     */
    private volatile GameStateManager gameStateManager = null;

    private final Gson gson = new Gson();
    private boolean isRunning = false;

    // ─── Server Lifecycle ─────────────────────────────────────────────────────

    public void start() {
        isRunning = true;
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ [Server] Started on port " + PORT);
            while (isRunning) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("❌ [Server] Failed to bind port: " + e.getMessage());
        }
    }

    // ─── Message Routing ──────────────────────────────────────────────────────

    /**
     * مرکزی‌ترین متد سرور.
     * پیام JSON ورودی را parse کرده و بر اساس فیلد "type" به handler مناسب می‌فرستد.
     * اگر بازی شروع نشده باشد پیام به handleLobbyMessage می‌رود؛ در غیر این صورت handleGameMessage.
     * چت در هر دو مرحله پشتیبانی می‌شود.
     */
    public void routeMessage(String clientId, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("type")) {
                System.err.println("[Server] Message without 'type' from: " + clientId);
                sendToClient(clientId, gson.toJson(
                        new ErrorResponse("Invalid message: missing 'type' field.")));
                return;
            }

            String type = obj.get("type").getAsString();
            System.out.println("[Server] Routing '" + type + "' from " + clientId);

            // چت در هر دو مرحله لابی و بازی از طریق LobbyManager broadcast می‌شود
            if ("CHAT_SEND".equals(type)) {
                ChatSendRequest req = gson.fromJson(json, ChatSendRequest.class);
                lobbyManager.processChatMessage(clientId, req.getText());
                return;
            }

            if (gameStateManager == null) {
                handleLobbyMessage(clientId, type, json);
            } else {
                handleGameMessage(clientId, type, json);
            }

        } catch (Exception e) {
            System.err.println("[Server] Error routing message from " + clientId + ": " + e.getMessage());
            sendToClient(clientId, gson.toJson(new ErrorResponse("Internal server error.")));
        }
    }

    /** مسیردهی پیام‌های مرحله لابی */
    private void handleLobbyMessage(String clientId, String type, String json) {
        switch (type) {
            case "JOIN_LOBBY" -> {
                JoinLobbyRequest req = gson.fromJson(json, JoinLobbyRequest.class);
                lobbyManager.addPlayer(clientId, req.getUsername());
            }
            case "TOGGLE_READY" -> lobbyManager.toggleReady(clientId);
            case "START_GAME"   -> {
                if (lobbyManager.canStartGame(clientId)) {
                    // انتقال از لابی به بازی — ساخت GameStateManager
                    this.gameStateManager = new GameStateManager(this, lobbyManager.getLobbyPlayers());
                    lobbyManager.notifyGameStarted();
                    gameStateManager.initializeGame();
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse(
                            "Cannot start game: not all players are ready, or you are not the host.")));
                }
            }
            default -> System.out.println("[Server] Unknown lobby message type: " + type + " from " + clientId);
        }
    }

    /** مسیردهی پیام‌های مرحله بازی */
    private void handleGameMessage(String clientId, String type, String json) {
        switch (type) {
            case "END_TURN"         -> gameStateManager.handleEndTurn(clientId);
            case "ATTACK_REQUEST"   -> gameStateManager.handleAttackRequest(clientId,
                    gson.fromJson(json, AttackRequest.class));
            case "ITEM_USE"         -> gameStateManager.handleItemUseRequest(clientId,
                    gson.fromJson(json, ItemUseRequest.class));
            case "DIPLOMACY_ACTION" -> gameStateManager.handleDiplomacyRequest(clientId,
                    gson.fromJson(json, DiplomacyRequest.class));
            case "TRADE_OFFER"      -> gameStateManager.handleTradeOffer(clientId,
                    gson.fromJson(json, TradeOfferRequest.class));
            case "TRADE_RESPONSE"   -> gameStateManager.handleTradeResponse(clientId,
                    gson.fromJson(json, TradeResponseRequest.class));
            default -> System.out.println("[Server] Unknown game message type: " + type + " from " + clientId);
        }
    }

    // ─── Client Management ────────────────────────────────────────────────────

    public void addClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        System.out.println("✅ [Server] Client connected: " + clientId + " | Total: " + clients.size());
    }

    /**
     * هنگام قطع اتصال کلاینت:
     * از map حذف شده و لابی از قطعی مطلع می‌شود.
     */
    public void removeClient(String clientId) {
        clients.remove(clientId);
        lobbyManager.removePlayer(clientId);
        System.out.println("⚠️ [Server] Client disconnected: " + clientId + " | Total: " + clients.size());
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    public void broadcast(String jsonMessage) {
        clients.values().forEach(handler -> handler.sendMessage(jsonMessage));
    }

    public void broadcastExcept(String jsonMessage, String excludeClientId) {
        clients.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(excludeClientId))
                .forEach(entry -> entry.getValue().sendMessage(jsonMessage));
    }

    public void sendToClient(String clientId, String jsonMessage) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.sendMessage(jsonMessage);
        }
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public GameStateManager getGameStateManager() { return gameStateManager; }
    public LobbyManager     getLobbyManager()     { return lobbyManager; }
}