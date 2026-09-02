package network.server;

import network.messages.lobby.LobbyPlayer;
import network.messages.lobby.LobbyUpdateBroadcast;
import network.messages.lobby.ChatMessageBroadcast;
import com.google.gson.Gson;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class LobbyManager {
    private final GameServer server;
    private final ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers;
    private final Gson gson;
    private boolean isGameStarted = false;

    public LobbyManager(GameServer server) {
        this.server = server;
        this.lobbyPlayers = new ConcurrentHashMap<>();
        this.gson = new Gson();
    }

    public synchronized void addPlayer(String clientId, String username) {
        if (isGameStarted) return; // ورود در حین بازی ممنوع
        boolean isHost = lobbyPlayers.isEmpty(); // اولین نفر هاست است
        LobbyPlayer newPlayer = new LobbyPlayer(clientId, username, isHost);
        lobbyPlayers.put(clientId, newPlayer);
        broadcastLobbyState();
    }

    public synchronized void removePlayer(String clientId) {
        LobbyPlayer removed = lobbyPlayers.remove(clientId);
        if (removed != null && removed.isHost() && !lobbyPlayers.isEmpty()) {
            // انتقال هاست به نفر بعدی در صورت خروج هاست
            String nextHostId = lobbyPlayers.keySet().iterator().next();
            lobbyPlayers.get(nextHostId).setHost(true);
        }
        broadcastLobbyState();
    }

    public synchronized void toggleReady(String clientId) {
        LobbyPlayer player = lobbyPlayers.get(clientId);
        if (player != null) {
            player.setReady(!player.isReady());
            broadcastLobbyState();
        }
    }

    public void processChatMessage(String clientId, String text) {
        LobbyPlayer player = lobbyPlayers.get(clientId);
        if (player != null) {
            String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            ChatMessageBroadcast chatMsg = new ChatMessageBroadcast(player.getUsername(), time, text);
            server.broadcast(gson.toJson(chatMsg));
        }
    }

    public synchronized boolean canStartGame(String clientId) {
        LobbyPlayer requester = lobbyPlayers.get(clientId);
        if (requester == null || !requester.isHost()) return false;

        // بررسی اینکه همه بازیکنان آماده هستند
        for (LobbyPlayer p : lobbyPlayers.values()) {
            if (!p.isReady()) return false;
        }
        return true;
    }

    private void broadcastLobbyState() {
        List<LobbyPlayer> currentPlayers = new ArrayList<>(lobbyPlayers.values());
        LobbyUpdateBroadcast update = new LobbyUpdateBroadcast(currentPlayers);
        server.broadcast(gson.toJson(update));
    }
}