package network.client;

import com.google.gson.Gson;
import controller.LobbyController;
import network.messages.game.*;
import network.messages.lobby.ChatMessageBroadcast;
import network.messages.lobby.LobbyUpdateBroadcast;
import view.HUDPanel;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Central dispatcher for all messages received from the server on the client side.
 * All calls arrive on the EDT (via SwingUtilities.invokeLater in NetworkManager.ListenerTask).
 */
public class ClientMessageDispatcher implements ServerMessageHandler {

    private final LobbyController lobbyController;
    private final Gson gson = new Gson();

    /** The client's own player ID — set after server assigns it. */
    private String myPlayerId = null;

    /** Reference to HUD for B23/B24/B25 updates — set by MainFrame after game starts. */
    private HUDPanel hudPanel = null;

    // ─── Callbacks ────────────────────────────────────────────────────────────

    private Runnable                           onGameStarted;
    private Consumer<GameStateBroadcast>       onGameStateUpdate;
    private Runnable                           onDisconnected;
    private Consumer<TradeInboxBroadcast>      onTradeInboxUpdate;
    private Consumer<DiplomacyBroadcast>       onDiplomacyEvent;

    public ClientMessageDispatcher(LobbyController lobbyController) {
        this.lobbyController = lobbyController;
    }

    // ─── Callback Registration ────────────────────────────────────────────────

    public void setOnGameStarted(Runnable cb)                        { onGameStarted = cb; }
    public void setOnGameStateUpdate(Consumer<GameStateBroadcast> cb){ onGameStateUpdate = cb; }
    public void setOnDisconnected(Runnable cb)                       { onDisconnected = cb; }
    public void setOnTradeInboxUpdate(Consumer<TradeInboxBroadcast> cb){ onTradeInboxUpdate = cb; }
    public void setOnDiplomacyEvent(Consumer<DiplomacyBroadcast> cb) { onDiplomacyEvent = cb; }
    public void setMyPlayerId(String id)                             { myPlayerId = id; }
    public void setHudPanel(HUDPanel panel)                          { hudPanel = panel; }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    @Override
    public void onMessage(String messageType, String rawJson) {
        switch (messageType) {

            // ── Lobby ──────────────────────────────────────────────────────────
            case "LOBBY_UPDATE" -> {
                LobbyUpdateBroadcast update = gson.fromJson(rawJson, LobbyUpdateBroadcast.class);
                lobbyController.handleLobbyUpdate(update);
            }

            // ── Chat (both lobby and in-game) — B25 ───────────────────────────
            case "CHAT_MESSAGE" -> {
                ChatMessageBroadcast chat = gson.fromJson(rawJson, ChatMessageBroadcast.class);
                // Format: [HH:mm] Username: message
                String formatted = String.format("[%s] %s: %s\n",
                        chat.getTimestamp(), chat.getSenderName(), chat.getText());
                // Route to lobby panel (if still in lobby) or HUD chat drawer
                lobbyController.handleChatMessage(chat);   // lobby panel
                if (hudPanel != null) {                     // in-game chat — B25
                    hudPanel.appendGameChatMessage(formatted);
                }
            }

            // ── Game Start ────────────────────────────────────────────────────
            case "GAME_START_BROADCAST" -> {
                System.out.println("[Client] Server started the game — switching to game view.");
                if (onGameStarted != null) onGameStarted.run();
            }

            // ── Game State Update — B24 ───────────────────────────────────────
            case "GAME_STATE_UPDATE" -> {
                GameStateBroadcast broadcast = gson.fromJson(rawJson, GameStateBroadcast.class);
                if (onGameStateUpdate != null) onGameStateUpdate.accept(broadcast);

                // B24: update turn indicator in HUD
                if (hudPanel != null) {
                    String activeId   = broadcast.getActivePlayerId();
                    boolean isMyTurn  = activeId != null && activeId.equals(myPlayerId);
                    // We display the active player's name; the server currently only sends
                    // the ID — use it as-is until a name-map is available on the client
                    hudPanel.setActiveTurnInfo(activeId, isMyTurn);

                    // Re-enable End Turn button if it's now our turn
                    if (isMyTurn) {
                        hudPanel.onOurTurnStarted();
                    }
                }
            }

            // ── War Report — B14 ──────────────────────────────────────────────
            case "WAT_REPORT" -> {
                WatReportBroadcast report = gson.fromJson(rawJson, WatReportBroadcast.class);
                if (report.getReports() != null) {
                    for (model.WatReport wr : report.getReports()) {
                        model.GameEventDispatcher.fireNotification(wr.toDisplayText());
                    }
                }
            }

            // ── Trade Inbox — B12 ─────────────────────────────────────────────
            case "TRADE_INBOX_UPDATE" -> {
                if (onTradeInboxUpdate != null) {
                    TradeInboxBroadcast inbox = gson.fromJson(rawJson, TradeInboxBroadcast.class);
                    onTradeInboxUpdate.accept(inbox);
                }
            }

            // ── Diplomacy — B13 + B23 ─────────────────────────────────────────
            case "DIPLOMACY_EVENT" -> {
                DiplomacyBroadcast event = gson.fromJson(rawJson, DiplomacyBroadcast.class);
                // Show global announcement
                model.GameEventDispatcher.fireNotification(event.getAnnouncementText());
                // Route to callback (for trade/alliance UI)
                if (onDiplomacyEvent != null) onDiplomacyEvent.accept(event);
                // B23: update HUD diplomacy panel
                if (hudPanel != null) {
                    updateHudDiplomacy(event);
                }
            }

            // ── General Notifications ─────────────────────────────────────────
            case "GAME_NOTIFICATION" -> {
                GameNotificationMessage notif = gson.fromJson(rawJson, GameNotificationMessage.class);
                model.GameEventDispatcher.fireNotification(notif.getText());
            }

            // ── Error ─────────────────────────────────────────────────────────
            case "ERROR_RESPONSE" -> {
                ErrorResponse err = gson.fromJson(rawJson, ErrorResponse.class);
                System.err.println("[Client] Server error: " + err.getErrorMessage());
                model.GameEventDispatcher.fireNotification("⚠️ " + err.getErrorMessage());
            }

            // ── Disconnect ────────────────────────────────────────────────────
            case "DISCONNECTED" -> {
                System.out.println("[Client] Disconnected from server.");
                if (onDisconnected != null) onDisconnected.run();
            }

            default ->
                    System.out.println("[Client] Unhandled message type: " + messageType);
        }
    }

    // ─── B23: Diplomacy HUD Helper ────────────────────────────────────────────

    /**
     * Updates the HUD diplomacy panel when a DIPLOMACY_EVENT arrives.
     * Builds a map from playerId → [displayName, status] and pushes it to the HUD.
     */
    private void updateHudDiplomacy(DiplomacyBroadcast event) {
        if (hudPanel == null || myPlayerId == null) return;

        // Determine how this event affects our diplomatic relations
        // We only track the relation from our perspective
        String initiatorId   = event.getInitiatorId();
        String initiatorName = event.getInitiatorName();
        String targetId      = event.getTargetId();
        String targetName    = event.getTargetName();

        // Build a simple local diplomacy map (keyed by "the other player's ID")
        Map<String, String[]> statusMap = new LinkedHashMap<>();

        String otherPlayerId;
        String otherPlayerName;

        if (myPlayerId.equals(initiatorId)) {
            otherPlayerId   = targetId;
            otherPlayerName = targetName;
        } else if (myPlayerId.equals(targetId)) {
            otherPlayerId   = initiatorId;
            otherPlayerName = initiatorName;
        } else {
            // This event doesn't involve us directly — still show it
            otherPlayerId   = initiatorId;
            otherPlayerName = initiatorName;
        }

        String newStatus = switch (event.getEventType()) {
            case "WAR_DECLARED"    -> "Enemy";
            case "ALLIANCE_FORMED" -> "Allied";
            case "ALLIANCE_BROKEN" -> "Neutral";
            default                -> "Neutral";
        };

        statusMap.put(otherPlayerId, new String[]{ otherPlayerName, newStatus });
        hudPanel.updateDiplomacyStatus(statusMap);
    }
}