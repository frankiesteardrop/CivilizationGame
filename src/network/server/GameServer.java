package network.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ConcurrentHashMap;

public class GameServer {
    private static final int PORT = 8080;
    // استفاده از ConcurrentHashMap برای Thread-Safety طبق دستور داک
    private final ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private boolean isRunning = false;

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

    public void addClient(String clientId, ClientHandler handler) {
        clients.put(clientId, handler);
        System.out.println("✅ [Server] Client connected: " + clientId + " | Total: " + clients.size());
    }

    public void removeClient(String clientId) {
        clients.remove(clientId);
        System.out.println("⚠️ [Server] Client disconnected: " + clientId + " | Total: " + clients.size());
    }

    public void broadcast(String jsonMessage) {
        clients.values().forEach(handler -> handler.sendMessage(jsonMessage));
    }

    public void broadcastExcept(String jsonMessage, String excludeClientId) {
        clients.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(excludeClientId))
                .forEach(entry -> entry.getValue().sendMessage(jsonMessage));
    }
}