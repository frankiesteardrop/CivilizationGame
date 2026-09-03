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

    /** The username this client sent when joining the lobby. Needed to determine host status. */
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

    // ─── Send to server ───────────────────────────────────────────────────────

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

    /**
     * Sends a map selection request to the server.
     * The server only accepts this if the sender is the host.
     *
     * @param mapId the ID of the chosen pre-designed map
     */
    public void selectMap(String mapId) {
        networkManager.sendRequest(gson.toJson(new SelectMapRequest(mapId)));
    }

    // ─── Receive from server (called on EDT) ──────────────────────────────────

    public void handleLobbyUpdate(LobbyUpdateBroadcast update) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                // Determine if we are the host by matching our username
                boolean iAmHost = update.getPlayers().stream()
                        .anyMatch(p -> p.isHost() && p.getUsername().equals(myUsername));
                lobbyPanel.updateLobbyState(update, iAmHost);
            }
        });
    }

    public void handleChatMessage(ChatMessageBroadcast msg) {
        SwingUtilities.invokeLater(() -> {
            if (lobbyPanel != null) {
                // Format: [HH:mm] Player1: message
                String formatted = String.format("[%s] %s: %s\n",
                        msg.getTimestamp(), msg.getSenderName(), msg.getText());
                lobbyPanel.appendChatMessage(formatted);
            }
        });
    }
}