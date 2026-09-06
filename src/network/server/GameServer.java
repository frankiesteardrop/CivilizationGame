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

public class GameServer {

    private static final int PORT = 8080;

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    private final DatabaseManager databaseManager = new DatabaseManager("civilization_sharif.db");

    private final LobbyManager   lobbyManager   = new LobbyManager(this, databaseManager);
    private final Gson            gson           = new Gson();

    private final UdpHeartbeatServer udpHeartbeat = new UdpHeartbeatServer();

    private volatile GameStateManager gameStateManager = null;
    private boolean isRunning = false;

    public void start() {
        isRunning = true;
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
        } finally {
            databaseManager.close();
        }
    }

    public void routeMessage(String clientId, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            if (!obj.has("type")) {
                sendToClient(clientId, gson.toJson(new ErrorResponse("Invalid message: missing 'type' field.")));
                return;
            }

            String type = obj.get("type").getAsString();

            if (!"JOIN_LOBBY".equals(type)) {
                if (!obj.has("token") || !JwtUtility.validateToken(obj.get("token").getAsString())) {
                    System.err.println("🚨 [Security] Unauthorized access attempt blocked from: " + clientId);
                    sendToClient(clientId, gson.toJson(new ErrorResponse("Unauthorized: Invalid or missing JWT Token!")));
                    return;
                }
            }

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
            System.err.println("[Server] Error routing message from " + clientId + ": " + e.getMessage());
            sendToClient(clientId, gson.toJson(new ErrorResponse("Internal server error.")));
        }
    }

    private void handleLobbyMessage(String clientId, String type, String json) {
        switch (type) {
            case "JOIN_LOBBY"  -> {
                JoinLobbyRequest req = gson.fromJson(json, JoinLobbyRequest.class);
                lobbyManager.addPlayer(clientId, req.getUsername(), req.getPassword());
            }
            case "TOGGLE_READY" -> lobbyManager.toggleReady(clientId);
            case "SELECT_MAP"   -> {
                SelectMapRequest req = gson.fromJson(json, SelectMapRequest.class);
                lobbyManager.setSelectedMap(clientId, req.getMapId());
            }
            case "START_GAME"   -> {
                if (lobbyManager.canStartGame(clientId)) {
                    String mapId = lobbyManager.getSelectedMapId();
                    this.gameStateManager = new GameStateManager(this, lobbyManager.getLobbyPlayers(), mapId, databaseManager);
                    lobbyManager.notifyGameStarted();
                    gameStateManager.initializeGame();
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot start game: not all players are ready, or you are not the host.")));
                }
            }
            default -> System.out.println("[Server] Unknown lobby message: " + type);
        }
    }

    private void handleGameMessage(String clientId, String type, String json) {
        switch (type) {
            case "END_TURN"         -> gameStateManager.handleEndTurn(clientId);
            case "ATTACK_REQUEST"   -> gameStateManager.handleAttackRequest(clientId, gson.fromJson(json, AttackRequest.class));
            case "ITEM_USE"         -> gameStateManager.handleItemUseRequest(clientId, gson.fromJson(json, ItemUseRequest.class));
            case "DIPLOMACY_ACTION" -> gameStateManager.handleDiplomacyRequest(clientId, gson.fromJson(json, DiplomacyRequest.class));

            // 🔴 ترید و سیستم لغو (Cancel Trade)
            case "TRADE_OFFER"      -> gameStateManager.handleTradeOffer(clientId, gson.fromJson(json, TradeOfferRequest.class));
            case "TRADE_RESPONSE"   -> gameStateManager.handleTradeResponse(clientId, gson.fromJson(json, TradeResponseRequest.class));
            case "TRADE_CANCEL"     -> gameStateManager.handleCancelTrade(clientId, gson.fromJson(json, CancelTradeRequest.class));

            case "CRAFT_ITEM"       -> gameStateManager.handleCraftItemRequest(clientId, gson.fromJson(json, CraftItemRequest.class));
            case "ALLIANCE_RESPONSE" -> gameStateManager.handleAllianceResponse(clientId, gson.fromJson(json, AllianceResponseRequest.class));

            case "MOVE_REQUEST"     -> gameStateManager.handleMoveRequest(clientId, gson.fromJson(json, MoveRequest.class));
            case "BUILD_REQUEST"    -> gameStateManager.handleBuildRequest(clientId, gson.fromJson(json, BuildRequest.class));
            case "TRAIN_REQUEST"    -> gameStateManager.handleTrainRequest(clientId, gson.fromJson(json, TrainRequest.class));
            case "CANCEL_PRODUCTION"-> gameStateManager.handleCancelProduction(clientId);
            default -> System.out.println("[Server] Unknown game message: " + type);
        }
    }

    public void addClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        System.out.println("✅ [Server] Client connected: " + clientId + " | Total: " + clients.size());
    }

    public void removeClient(String clientId) {
        clients.remove(clientId);
        lobbyManager.removePlayer(clientId);

        if (gameStateManager != null) {
            gameStateManager.handlePlayerDisconnected(clientId);
        }
        udpHeartbeat.removeClient(clientId);
        System.out.println("⚠️ [Server] Client disconnected: " + clientId + " | Total: " + clients.size());
    }

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

    public GameStateManager      getGameStateManager() { return gameStateManager; }
    public LobbyManager          getLobbyManager()     { return lobbyManager; }
    public UdpHeartbeatServer    getUdpHeartbeat()     { return udpHeartbeat; }
}