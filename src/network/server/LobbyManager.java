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

/**
 * مدیریت وضعیت لابی قبل از شروع بازی.
 * لیست بازیکنان، وضعیت Ready، انتخاب هاست و سیستم چت در این کلاس قرار دارند.
 */
public class LobbyManager {

    private final GameServer server;
    private final ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers;
    private final Gson gson;
    private boolean isGameStarted = false;

    public LobbyManager(GameServer server) {
        this.server       = server;
        this.lobbyPlayers = new ConcurrentHashMap<>();
        this.gson         = new Gson();
    }

    // ─── Player Management ────────────────────────────────────────────────────

    public synchronized void addPlayer(String clientId, String username) {
        if (isGameStarted) return; // ورود در حین بازی ممنوع
        boolean isHost = lobbyPlayers.isEmpty(); // اولین نفر هاست است
        LobbyPlayer newPlayer = new LobbyPlayer(clientId, username, isHost);
        lobbyPlayers.put(clientId, newPlayer);
        System.out.println("[Lobby] Player joined: " + username + " (host=" + isHost + ")");
        broadcastLobbyState();
    }

    public synchronized void removePlayer(String clientId) {
        LobbyPlayer removed = lobbyPlayers.remove(clientId);
        if (removed != null) {
            System.out.println("[Lobby] Player left: " + removed.getUsername());
            // اگر هاست قطع شد، هاست به نفر بعدی منتقل می‌شود
            if (removed.isHost() && !lobbyPlayers.isEmpty()) {
                String nextHostId = lobbyPlayers.keySet().iterator().next();
                lobbyPlayers.get(nextHostId).setHost(true);
                System.out.println("[Lobby] Host transferred to: " +
                        lobbyPlayers.get(nextHostId).getUsername());
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

    /**
     * بررسی می‌کند آیا بازی می‌تواند شروع شود.
     * شرط: درخواست‌دهنده هاست باشد و همه بازیکنان Ready باشند.
     */
    public synchronized boolean canStartGame(String clientId) {
        LobbyPlayer requester = lobbyPlayers.get(clientId);
        if (requester == null || !requester.isHost()) return false;
        if (lobbyPlayers.size() < 2) return false; // حداقل ۲ بازیکن

        for (LobbyPlayer p : lobbyPlayers.values()) {
            if (!p.isReady()) return false;
        }
        return true;
    }

    /**
     * وقتی GameServer بازی را شروع می‌کند این متد را صدا می‌زند
     * تا ورود کلاینت‌های جدید به لابی بلاک شود.
     */
    public synchronized void notifyGameStarted() {
        this.isGameStarted = true;
        System.out.println("[Lobby] Game started. New connections to lobby are now blocked.");
    }

    // ─── Accessors ────────────────────────────────────────────────────────────

    /**
     * دسترسی به نقشه بازیکنان برای ساخت GameStateManager.
     * ConcurrentHashMap داده می‌شود تا GameStateManager بتواند thread-safe کار کند.
     */
    public ConcurrentHashMap<String, LobbyPlayer> getLobbyPlayers() {
        return lobbyPlayers;
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void broadcastLobbyState() {
        List<LobbyPlayer> currentPlayers = new ArrayList<>(lobbyPlayers.values());
        LobbyUpdateBroadcast update = new LobbyUpdateBroadcast(currentPlayers);
        server.broadcast(gson.toJson(update));
    }
}