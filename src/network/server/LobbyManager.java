package network.server;

import model.maps.PreDesignedMaps;
import network.messages.lobby.LobbyPlayer;
import network.messages.lobby.LobbyUpdateBroadcast;
import network.messages.lobby.ChatMessageBroadcast;
import network.messages.lobby.PlayerIdAssignedMessage;
import com.google.gson.Gson;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the pre-game lobby: player list, ready status, host assignment,
 * map selection, and chat.
 */
public class LobbyManager {

    private final GameServer server;
    private final ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers;
    private final Gson gson;
    private boolean isGameStarted = false;

    /** The map ID selected by the host. Defaults to the first available map. */
    private String selectedMapId = PreDesignedMaps.getDefaultMapId();

    public LobbyManager(GameServer server) {
        this.server       = server;
        this.lobbyPlayers = new ConcurrentHashMap<>();
        this.gson         = new Gson();
    }

    // ─── Player Management ────────────────────────────────────────────────────

    public synchronized void addPlayer(String clientId, String username) {
        if (isGameStarted) return;
        boolean isHost = lobbyPlayers.isEmpty();
        LobbyPlayer newPlayer = new LobbyPlayer(clientId, username, isHost);
        lobbyPlayers.put(clientId, newPlayer);
        System.out.println("[Lobby] Player joined: " + username + " (host=" + isHost + ")");

        // ارسال شناسه معتبر (UUID) به کلاینت متصل شده
        server.sendToClient(clientId, gson.toJson(new PlayerIdAssignedMessage(clientId)));

        broadcastLobbyState();
    }

    public synchronized void removePlayer(String clientId) {
        LobbyPlayer removed = lobbyPlayers.remove(clientId);
        if (removed != null) {
            System.out.println("[Lobby] Player left: " + removed.getUsername());
            if (removed.isHost() && !lobbyPlayers.isEmpty()) {
                String nextHostId = lobbyPlayers.keySet().iterator().next();
                lobbyPlayers.get(nextHostId).setHost(true);
                System.out.println("[Lobby] Host transferred to: "
                        + lobbyPlayers.get(nextHostId).getUsername());
            }
            broadcastLobbyState();
        }
    }

    public synchronized void toggleReady(String clientId) {
        LobbyPlayer player = lobbyPlayers.get(clientId);
        if (player != null) {
            player.setReady(!player.isReady());
            broadcastLobbyState();
        }
    }

    // ─── Map Selection (B10) ──────────────────────────────────────────────────

    public synchronized void setSelectedMap(String clientId, String mapId) {
        LobbyPlayer requester = lobbyPlayers.get(clientId);
        if (requester == null || !requester.isHost()) {
            System.out.println("[Lobby] Non-host tried to change map: " + clientId);
            return;
        }
        this.selectedMapId = mapId;
        System.out.println("[Lobby] Host selected map: " + mapId);
        broadcastLobbyState();
    }

    public String getSelectedMapId() {
        return selectedMapId;
    }

    // ─── Chat ─────────────────────────────────────────────────────────────────

    public void processChatMessage(String clientId, String text) {
        LobbyPlayer player = lobbyPlayers.get(clientId);
        if (player != null && text != null && !text.isBlank()) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            ChatMessageBroadcast chatMsg = new ChatMessageBroadcast(
                    player.getUsername(), time, text);
            server.broadcast(gson.toJson(chatMsg));
        }
    }

    // ─── Game Start Validation ────────────────────────────────────────────────

    public synchronized boolean canStartGame(String clientId) {
        LobbyPlayer requester = lobbyPlayers.get(clientId);
        if (requester == null || !requester.isHost()) return false;
        if (lobbyPlayers.size() < 2) return false;

        for (LobbyPlayer p : lobbyPlayers.values()) {
            if (!p.isReady()) return false;
        }
        return true;
    }

    public synchronized void notifyGameStarted() {
        this.isGameStarted = true;
        System.out.println("[Lobby] Game started. New connections to lobby are blocked.");
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    public ConcurrentHashMap<String, LobbyPlayer> getLobbyPlayers() {
        return lobbyPlayers;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void broadcastLobbyState() {
        List<LobbyPlayer> currentPlayers = new ArrayList<>(lobbyPlayers.values());
        LobbyUpdateBroadcast update = new LobbyUpdateBroadcast(currentPlayers, selectedMapId);
        server.broadcast(gson.toJson(update));
    }
}