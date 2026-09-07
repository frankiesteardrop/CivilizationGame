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
import java.util.concurrent.CountDownLatch;

public class GameServer {

    private static final int PORT = 8080;

    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final DatabaseManager databaseManager = new DatabaseManager("civilization_sharif.db");
    private final LobbyManager lobbyManager = new LobbyManager(this, databaseManager);
    private final Gson gson = new Gson();
    private final UdpHeartbeatServer udpHeartbeat = new UdpHeartbeatServer();

    private volatile GameStateManager gameStateManager = null;
    private volatile boolean isRunning = false;
    private ServerSocket serverSocket;
    private Thread udpMonitorThread;

    public void start(CountDownLatch serverReadySignal) {
        isRunning = true;
        udpHeartbeat.start();

        try {
            serverSocket = new ServerSocket(PORT);
            System.out.println("✅ [Server] TCP server started on port " + PORT);

            if (serverReadySignal != null) {
                serverReadySignal.countDown();
            }

            udpMonitorThread = new Thread(() -> {
                while (isRunning) {
                    try {
                        Thread.sleep(5000);
                        java.util.List<String> deadClients = new java.util.ArrayList<>();
                        for (String clientId : clients.keySet()) {
                            if (!udpHeartbeat.isClientAlive(clientId)) {
                                System.out.println("🚨 [Server-UDP] Heartbeat dead for client: " + clientId);
                                deadClients.add(clientId);
                            }
                        }
                        for (String deadId : deadClients) {
                            removeClient(deadId);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }, "udp-monitor-thread");
            udpMonitorThread.setDaemon(true);
            udpMonitorThread.start();

            while (isRunning && !serverSocket.isClosed()) {
                Socket clientSocket = serverSocket.accept();
                ClientHandler handler = new ClientHandler(clientSocket, this);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            if (isRunning) System.err.println("❌ [Server] Failed to bind port or socket error: " + e.getMessage());
        } finally {
            stop();
        }
    }

    public void stop() {
        if (!isRunning) return;
        isRunning = false;
        System.out.println("⚠️ [Server] Shutting down and cleaning up resources...");

        if (udpMonitorThread != null) udpMonitorThread.interrupt();
        udpHeartbeat.stop();

        for (ClientHandler handler : clients.values()) {
            handler.close();
        }
        clients.clear();

        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException e) {
            System.err.println("❌ [Server] Error closing server socket: " + e.getMessage());
        }

        databaseManager.close();
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
                if (!obj.has("token") || !JwtUtility.validateToken(obj.get("token").getAsString(), clientId)) {
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
            case "JOIN_LOBBY" -> {
                JoinLobbyRequest req = gson.fromJson(json, JoinLobbyRequest.class);
                lobbyManager.addPlayer(clientId, req.getUsername(), req.getPassword());
            }
            case "TOGGLE_READY" -> lobbyManager.toggleReady(clientId);
            case "SELECT_MAP" -> {
                SelectMapRequest req = gson.fromJson(json, SelectMapRequest.class);
                lobbyManager.setSelectedMap(clientId, req.getMapId());
            }
            case "START_GAME" -> {
                if (lobbyManager.canStartGame(clientId)) {
                    String mapId = lobbyManager.getSelectedMapId();
                    this.gameStateManager = new GameStateManager(this, lobbyManager.getLobbyPlayers(), mapId, databaseManager);
                    lobbyManager.notifyGameStarted();
                    gameStateManager.initializeGame();
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot start game: not all players are ready, or you are not the host.")));
                }
            }
            case "LOAD_GAME" -> {
                LobbyPlayer player = lobbyManager.getLobbyPlayers().get(clientId);
                if (player != null && player.isHost()) {
                    JsonObject reqObj = JsonParser.parseString(json).getAsJsonObject();
                    String slot = reqObj.get("slot").getAsString();
                    String stateJson = databaseManager.loadGameSession(slot);
                    if (stateJson != null) {
                        try {
                            JsonObject root = JsonParser.parseString(stateJson).getAsJsonObject();
                            if (root.has("playerNames")) {
                                JsonObject savedPlayers = root.getAsJsonObject("playerNames");
                                boolean isMatch = true;
                                java.util.Set<String> lobbyUsernames = new java.util.HashSet<>();
                                for (LobbyPlayer lp : lobbyManager.getLobbyPlayers().values()) {
                                    lobbyUsernames.add(lp.getUsername());
                                }

                                if (savedPlayers.size() != lobbyUsernames.size()) {
                                    isMatch = false;
                                } else {
                                    for (String savedKey : savedPlayers.keySet()) {
                                        if (!lobbyUsernames.contains(savedKey)) {
                                            isMatch = false;
                                            break;
                                        }
                                    }
                                }

                                if (!isMatch) {
                                    sendToClient(clientId, gson.toJson(new ErrorResponse("Load failed: The players in this lobby do not match the players in the save file.")));
                                    return;
                                }
                            }
                        } catch (Exception ex) {
                            sendToClient(clientId, gson.toJson(new ErrorResponse("Corrupted save file. Cannot validate players.")));
                            return;
                        }

                        this.gameStateManager = new GameStateManager(this, lobbyManager.getLobbyPlayers(), "alpha", databaseManager);
                        if (gameStateManager.loadGameFromJson(stateJson)) {
                            lobbyManager.notifyGameStarted();
                        } else {
                            sendToClient(clientId, gson.toJson(new ErrorResponse("Failed to parse save file data.")));
                            this.gameStateManager = null;
                        }
                    } else {
                        sendToClient(clientId, gson.toJson(new ErrorResponse("Save file not found on server.")));
                    }
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse("Only the host can load a game.")));
                }
            }
            default -> System.out.println("[Server] Unknown lobby message: " + type);
        }
    }

    private void handleGameMessage(String clientId, String type, String json) {
        switch (type) {
            case "LOAD_GAME" -> {
                sendToClient(clientId, gson.toJson(new ErrorResponse("Cannot load game while a match is already running. Return to lobby first.")));
            }
            case "SAVE_GAME" -> {
                LobbyPlayer player = lobbyManager.getLobbyPlayers().get(clientId);
                if (player != null && player.isHost()) {
                    JsonObject reqObj = JsonParser.parseString(json).getAsJsonObject();
                    String slot = reqObj.get("slot").getAsString();
                    gameStateManager.saveGameToJson(slot);
                    sendToClient(clientId, gson.toJson(new GameNotificationMessage("✅ Game saved successfully to " + slot)));
                } else {
                    sendToClient(clientId, gson.toJson(new ErrorResponse("Only the host can save the game.")));
                }
            }
            case "END_TURN" -> gameStateManager.handleEndTurn(clientId);
            case "ATTACK_REQUEST" -> gameStateManager.handleAttackRequest(clientId, gson.fromJson(json, AttackRequest.class));
            case "CAPTURE_HEX" -> gameStateManager.handleCaptureHexRequest(clientId, gson.fromJson(json, CaptureHexRequest.class));
            case "ITEM_USE" -> gameStateManager.handleItemUseRequest(clientId, gson.fromJson(json, ItemUseRequest.class));
            case "DIPLOMACY_ACTION" -> gameStateManager.handleDiplomacyRequest(clientId, gson.fromJson(json, DiplomacyRequest.class));
            case "TRADE_OFFER" -> gameStateManager.handleTradeOffer(clientId, gson.fromJson(json, TradeOfferRequest.class));
            case "TRADE_RESPONSE" -> gameStateManager.handleTradeResponse(clientId, gson.fromJson(json, TradeResponseRequest.class));
            case "TRADE_CANCEL" -> gameStateManager.handleCancelTrade(clientId, gson.fromJson(json, CancelTradeRequest.class));
            case "CRAFT_ITEM" -> gameStateManager.handleCraftItemRequest(clientId, gson.fromJson(json, CraftItemRequest.class));
            case "ALLIANCE_RESPONSE" -> gameStateManager.handleAllianceResponse(clientId, gson.fromJson(json, AllianceResponseRequest.class));
            case "MOVE_REQUEST" -> gameStateManager.handleMoveRequest(clientId, gson.fromJson(json, MoveRequest.class));
            case "BUILD_REQUEST" -> gameStateManager.handleBuildRequest(clientId, gson.fromJson(json, BuildRequest.class));
            case "TRAIN_REQUEST" -> gameStateManager.handleTrainRequest(clientId, gson.fromJson(json, TrainRequest.class));
            case "CANCEL_PRODUCTION" -> gameStateManager.handleCancelProduction(clientId);
            case "TRIBE_ACTION" -> gameStateManager.handleTribeActionRequest(clientId, gson.fromJson(json, TribeActionRequest.class));
            case "NPC_TRADE" -> gameStateManager.handleNpcTradeRequest(clientId, gson.fromJson(json, NpcTradeRequest.class));
            default -> System.out.println("[Server] Unknown game message: " + type);
        }
    }

    public synchronized void addClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        System.out.println("✅ [Server] Client connected: " + clientId + " | Total: " + clients.size());
    }

    public synchronized void removeClient(String clientId) {
        if (!clients.containsKey(clientId)) return;
        ClientHandler handler = clients.remove(clientId);
        if (handler != null) handler.close();

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

    public void sendToClient(String clientId, String jsonMessage) {
        ClientHandler handler = clients.get(clientId);
        if (handler != null) {
            handler.sendMessage(jsonMessage);
        }
    }

    public GameStateManager getGameStateManager() { return gameStateManager; }
    public LobbyManager getLobbyManager() { return lobbyManager; }
    public UdpHeartbeatServer getUdpHeartbeat() { return udpHeartbeat; }
}