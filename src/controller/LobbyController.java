package controller;

import com.google.gson.Gson;
import network.client.NetworkManager;
import network.messages.lobby.ChatSendRequest;
import network.messages.lobby.StartGameRequest;
import network.messages.lobby.ToggleReadyRequest;
import network.messages.lobby.LobbyUpdateBroadcast;
import network.messages.lobby.ChatMessageBroadcast;
import view.LobbyPanel;

import javax.swing.SwingUtilities;

public class LobbyController {
    private final NetworkManager networkManager;
    private LobbyPanel lobbyPanel;
    private final Gson gson;

    public LobbyController(NetworkManager networkManager) {
        this.networkManager = networkManager;
        this.gson = new Gson();
    }

    public void setView(LobbyPanel lobbyPanel) {
        this.lobbyPanel = lobbyPanel;
    }

    // --- ارسال به سرور ---
    public void toggleReady() {
        networkManager.sendRequest(gson.toJson(new ToggleReadyRequest()));
    }

    public void sendChatMessage(String text) {
        if (text != null && !text.trim().isEmpty()) {
            networkManager.sendRequest(gson.toJson(new ChatSendRequest(text.trim())));
        }
    }

    public void requestStartGame() {
        networkManager.sendRequest(gson.toJson(new StartGameRequest()));
    }

    // --- دریافت از سرور (اجرا روی EDT) ---
    public void handleLobbyUpdate(LobbyUpdateBroadcast update) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                lobbyPanel.updatePlayerList(update.getPlayers());
            }
        });
    }

    public void handleChatMessage(ChatMessageBroadcast msg) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                // فرمت دقیق داک: [HH:mm] Player1: message
                String formattedMsg = String.format("[%s] %s: %s\n",
                        msg.getTimestamp(), msg.getSenderName(), msg.getText());
                lobbyPanel.appendChatMessage(formattedMsg);
            }
        });
    }
}