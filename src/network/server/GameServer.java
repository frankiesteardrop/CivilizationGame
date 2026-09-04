package network.server;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import network.messages.game.*;
import network.messages.lobby.*;
import network.udp.UdpHeartbeatServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core game server — accepts TCP connections, routes messages, and manages
 * the UDP heartbeat server for connection health monitoring.
 */
public class GameServer {

    private static final int PORT = 8080;

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final LobbyManager   lobbyManager   = new LobbyManager(this);
    private final Gson            gson           = new Gson();

    /** Started in {@link #start()} alongside the TCP accept loop. */
    private final UdpHeartbeatServer udpHeartbeat = new UdpHeartbeatServer();

    private volatile GameStateManager gameStateManager = null;
    private boolean isRunning = false;

    // ─── Server Lifecycle ─────────────────────────────────────────────────────

    public void start() {
        isRunning = true;

        // Start UDP heartbeat server on a daemon thread (B6)
        udpHeartbeat.start();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("✅ [Server] TCP server started on port " + PORT);
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
     * Parses the "type" field and dispatches to the appropriate handler.
     * Chat is handled at all stages; game messages are only accepted after
     * the game has started.
     */
    public void routeMessage(String clientId, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("type")) {
                sendToClient(clientId, gson.toJson(
                        new ErrorResponse("Invalid message: missing 'type' field.")));
                return;
            }

            String type = obj.get("type").getAsString();
            System.out.println("[Server] Routing '" + type + "' from " + clientId);

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
            System.err.println("[Server] Error routing message from " + clientId
                    + ": " + e.getMessage());
            sendToClient(clientId, gson.toJson(new ErrorResponse("Internal server error.")));
        }
    }

    private void handleLobbyMessage(String clientId, String type, String json) {
        switch (type) {
            case "JOIN_LOBBY"  -> {
                JoinLobbyRequest req = gson.fromJson(json, JoinLobbyRequest.class);
                lobbyManager.addPlayer(clientId, req.getUsername());
            }
            case "TOGGLE_READY" -> lobbyManager.toggleReady(clientId);
            case "SELECT_MAP"   -> {
                SelectMapRequest req = gson.fromJson(json, SelectMapRequest.class);
                lobbyManager.setSelectedMap(clientId, req.getMapId());
            }
            case "START_GAME"   -> {
                if (lobbyManager.canStartGame(clientId)) {
                    String mapId = lobbyManager.getSelectedMapId();
                    this.gameStateManager = new GameStateManager(
                            this, lobbyManager.getLobbyPlayers(), mapId);
                    lobbyManager.notifyGameStarted();
                    gameStateManager.initializeGame();
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse(
                            "Cannot start game: not all players are ready, or you are not the host.")));
                }
            }
            default -> System.out.println("[Server] Unknown lobby message: " + type);
        }
    }

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
            case "CRAFT_ITEM"       -> gameStateManager.handleCraftItemRequest(clientId,
                    gson.fromJson(json, CraftItemRequest.class));
            case "ALLIANCE_RESPONSE" -> gameStateManager.handleAllianceResponse(clientId,
                    gson.fromJson(json, AllianceResponseRequest.class));
            default -> System.out.println("[Server] Unknown game message: " + type);
        }
    }

    // ─── Client Management ────────────────────────────────────────────────────

    public void addClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        System.out.println("✅ [Server] Client connected: " + clientId
                + " | Total: " + clients.size());
    }

    public void removeClient(String clientId) {
        clients.remove(clientId);
        lobbyManager.removePlayer(clientId);
        udpHeartbeat.removeClient(clientId); // (B6) clean up UDP tracking
        System.out.println("⚠️ [Server] Client disconnected: " + clientId
                + " | Total: " + clients.size());
    }

    // ─── Messaging ────────────────────────────────────────────────────────────

    public void broadcast(String jsonMessage) {
        clients.values().forEach(handler -> handler.sendMessage(jsonMessage));
    }

    public void broadcastExcept(String jsonMessage, String excludeClientId) {
        clients.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeClientId))
                .forEach(e -> e.getValue().sendMessage(jsonMessage));
    }

    public void sendToClient(String clientId, String jsonMessage) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) handler.sendMessage(jsonMessage);
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public GameStateManager      getGameStateManager() { return gameStateManager; }
    public LobbyManager          getLobbyManager()     { return lobbyManager; }
    public UdpHeartbeatServer    getUdpHeartbeat()     { return udpHeartbeat; }
}