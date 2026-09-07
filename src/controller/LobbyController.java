package controller;

import com.google.gson.Gson;
import network.client.NetworkManager;
import network.messages.lobby.ChatSendRequest;
import network.messages.lobby.SelectMapRequest;
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

    private String myUsername = "";

    public LobbyController(NetworkManager networkManager) {
        this.networkManager = networkManager;
        this.gson = new Gson();
    }

    public void setView(LobbyPanel lobbyPanel) {
        this.lobbyPanel = lobbyPanel;
    }

    public void setMyUsername(String username) {
        this.myUsername = username;
    }

    public String getMyUsername() {
        return myUsername;
    }

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

    public void selectMap(String mapId) {
        networkManager.sendRequest(gson.toJson(new SelectMapRequest(mapId)));
    }

    public void handleLobbyUpdate(LobbyUpdateBroadcast update) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                boolean iAmHost = update.getPlayers().stream()
                        .anyMatch(p -> p.isHost() && p.getUsername().equals(myUsername));
                lobbyPanel.updateLobbyState(update, iAmHost);
            }
        });
    }

    public void handleChatMessage(ChatMessageBroadcast msg) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                String formatted = String.format("[%s] %s: %s\n",
                        msg.getTimestamp(), msg.getSenderName(), msg.getText());
                lobbyPanel.appendChatMessage(formatted);
            }
        });
    }
}