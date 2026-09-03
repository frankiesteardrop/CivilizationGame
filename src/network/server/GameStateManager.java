package network.server;

import com.google.gson.Gson;
import model.GameMap;
import model.Hex;
import model.Unit;
import model.Building;
import network.messages.game.ErrorResponse;
import network.messages.game.GameStateBroadcast;
import network.messages.lobby.LobbyPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class GameStateManager {
    private final GameServer server;
    private final Gson gson;
    private GameMap masterMap;
    private final List<LobbyPlayer> players;
    private int currentPlayerIndex;

    public GameStateManager(GameServer server, ConcurrentHashMap<String, LobbyPlayer> lobbyPlayers) {
        this.server = server;
        this.gson = new Gson();
        this.players = new ArrayList<>(lobbyPlayers.values());
        this.currentPlayerIndex = 0;
    }

    // 1. انتقال از لابی به بازی
    public synchronized void initializeGame() {
        // مقداردهی اولیه مپ پایه (از کدهای فاز 2 شما)
        this.masterMap = new GameMap(20);

        // TODO: در ادامه، منطق اختصاص TownHall و 5 یونیت اولیه در فواصل دور برای هر پلیر پیاده می‌شود.

        System.out.println("✅ [Server] Game Initialized. Transitioning clients to Game UI...");
        broadcastCustomizedStates(); // ارسال وضعیت فیلتر شده اختصاصی
    }

    // 2. مدیریت نوبت
    public synchronized void handleEndTurn(String clientId) {
        String activeId = players.get(currentPlayerIndex).getId();

        // سرور باید بررسی کند آیا الان نوبت این پلیر است یا خیر
        if (!activeId.equals(clientId)) {
            server.sendToClient(clientId, gson.toJson(new ErrorResponse("It is not your turn!")));
            return;
        }

        // چرخش نوبت
        currentPlayerIndex = (currentPlayerIndex + 1) % players.size();

        // اگر همه بازیکنان نوبتشان تمام شد، Turn کلی بازی را جلو می‌بریم
        if (currentPlayerIndex == 0) {
            // منطق پایان ترن اصلی (تولید منابع، بلایای طبیعی، قبایل و...) از TurnController فاز 2
            masterMap.incrementTurn();
        }

        broadcastCustomizedStates();
    }

    // 3. اعمال Fog of War (جلوگیری از تقلب)
    public synchronized void broadcastCustomizedStates() {
        String activePlayerId = players.get(currentPlayerIndex).getId();

        for (LobbyPlayer player : players) {
            String clientId = player.getId();
            // تولید یک نسخه فیلتر شده از مپ برای این پلیر
            GameMap playerSpecificMap = filterMapForPlayer(masterMap, clientId);

            String mapJson = gson.toJson(playerSpecificMap);
            GameStateBroadcast update = new GameStateBroadcast(activePlayerId, masterMap.getCurrentTurn(), mapJson);

            server.sendToClient(clientId, gson.toJson(update));
        }
    }

    // منطق حیاتی: سرور هرگز مپ کامل را برای کلاینت نمی‌فرستد
    private GameMap filterMapForPlayer(GameMap master, String playerId) {
        // در یک پیاده‌سازی واقعی، ما اینجا یک Deep Copy از مپ می‌سازیم.
        // سپس هکس‌هایی که در شعاع دید (Vision Radius) یونیت‌ها و ساختمان‌های playerId نیستند را پیدا می‌کنیم.
        // برای هکس‌های خارج از دید:
        // 1. لیست Units آن هکس پاک (خالی) می‌شود.
        // 2. اگر Building دشمن در آن است و قبلاً کشف نشده، مخفی می‌شود.
        // 3. منابع (Resources) پنهان می‌شوند.

        // توجه: کد دقیق Deep Copy بسته به متدهای GameMap شما پیاده‌سازی می‌شود.
        return master; // موقتاً مپ اصلی برگشت داده می‌شود تا DTOهای امن را بسازیم
    }
}