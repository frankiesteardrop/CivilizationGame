package network.client;

import com.google.gson.Gson;
import controller.LobbyController;
import network.messages.game.ErrorResponse;
import network.messages.game.GameStateBroadcast;
import network.messages.game.TradeInboxBroadcast;
import network.messages.game.DiplomacyBroadcast;
import network.messages.game.GameNotificationMessage;
import network.messages.lobby.ChatMessageBroadcast;
import network.messages.lobby.LobbyUpdateBroadcast;

/**
 * Central dispatcher for all messages received from the server on the client side.
 *
 * <p>Implements {@link ServerMessageHandler} so it can be registered with
 * {@link NetworkManager}. All calls arrive on the EDT (via
 * {@code SwingUtilities.invokeLater} in NetworkManager.ListenerTask).
 *
 * <p>Routing table:
 * <ul>
 *   <li>{@code LOBBY_UPDATE}        → LobbyController.handleLobbyUpdate()</li>
 *   <li>{@code CHAT_MESSAGE}        → LobbyController.handleChatMessage()</li>
 *   <li>{@code GAME_START_BROADCAST}→ onGameStarted callback (switches UI)</li>
 *   <li>{@code GAME_STATE_UPDATE}   → onGameStateUpdate callback (refreshes game view)</li>
 *   <li>{@code ERROR_RESPONSE}      → logs the error message</li>
 *   <li>{@code DISCONNECTED}        → onDisconnected callback</li>
 * </ul>
 */
public class ClientMessageDispatcher implements ServerMessageHandler {

    private final LobbyController lobbyController;
    private final Gson gson = new Gson();

    /** Called (on EDT) when the server broadcasts GAME_START_BROADCAST. */
    private Runnable onGameStarted;

    /**
     * Called (on EDT) whenever the server sends a full GAME_STATE_UPDATE.
     * The raw JSON string of {@link GameStateBroadcast} is passed as argument.
     */
    private java.util.function.Consumer<GameStateBroadcast> onGameStateUpdate;

    /** Called (on EDT) when the TCP connection to the server drops. */
    private Runnable onDisconnected;

    /** Called (on EDT) when this client's trade inbox changes. */
    private java.util.function.Consumer<TradeInboxBroadcast> onTradeInboxUpdate;

    /** Called (on EDT) when a diplomacy event is broadcast. */
    private java.util.function.Consumer<DiplomacyBroadcast> onDiplomacyEvent;

    public ClientMessageDispatcher(LobbyController lobbyController) {
        this.lobbyController = lobbyController;
    }

    // ─── Callback registration ────────────────────────────────────────────────

    public void setOnGameStarted(Runnable callback) {
        this.onGameStarted = callback;
    }

    public void setOnGameStateUpdate(java.util.function.Consumer<GameStateBroadcast> callback) {
        this.onGameStateUpdate = callback;
    }

    public void setOnDisconnected(Runnable callback) {
        this.onDisconnected = callback;
    }

    public void setOnTradeInboxUpdate(java.util.function.Consumer<TradeInboxBroadcast> callback) {
        this.onTradeInboxUpdate = callback;
    }

    public void setOnDiplomacyEvent(java.util.function.Consumer<DiplomacyBroadcast> callback) {
        this.onDiplomacyEvent = callback;
    }

    // ─── Dispatch ─────────────────────────────────────────────────────────────

    @Override
    public void onMessage(String messageType, String rawJson) {
        switch (messageType) {

            case "LOBBY_UPDATE" -> {
                LobbyUpdateBroadcast update = gson.fromJson(rawJson, LobbyUpdateBroadcast.class);
                lobbyController.handleLobbyUpdate(update);
            }

            case "CHAT_MESSAGE" -> {
                ChatMessageBroadcast chat = gson.fromJson(rawJson, ChatMessageBroadcast.class);
                lobbyController.handleChatMessage(chat);
            }

            case "GAME_START_BROADCAST" -> {
                System.out.println("[Client] Server started the game — switching to game view.");
                if (onGameStarted != null) {
                    onGameStarted.run();
                }
            }

            case "GAME_STATE_UPDATE" -> {
                if (onGameStateUpdate != null) {
                    GameStateBroadcast broadcast = gson.fromJson(rawJson, GameStateBroadcast.class);
                    onGameStateUpdate.accept(broadcast);
                }
            }

            case "ERROR_RESPONSE" -> {
                ErrorResponse err = gson.fromJson(rawJson, ErrorResponse.class);
                System.err.println("[Client] Server error: " + err.getErrorMessage());
            }

            case "TRADE_INBOX_UPDATE" -> {
                if (onTradeInboxUpdate != null) {
                    TradeInboxBroadcast inbox = gson.fromJson(rawJson, TradeInboxBroadcast.class);
                    onTradeInboxUpdate.accept(inbox);
                }
            }

            case "DIPLOMACY_EVENT" -> {
                DiplomacyBroadcast event = gson.fromJson(rawJson, DiplomacyBroadcast.class);
                // Show the announcement to the player and trigger any UI updates
                if (onDiplomacyEvent != null) {
                    onDiplomacyEvent.accept(event);
                }
            }

            case "GAME_NOTIFICATION" -> {
                GameNotificationMessage notif = gson.fromJson(rawJson, GameNotificationMessage.class);
                // Route to the game's notification system
                model.GameEventDispatcher.fireNotification(notif.getText());
            }

            case "DISCONNECTED" -> {
                System.out.println("[Client] Disconnected from server.");
                if (onDisconnected != null) {
                    onDisconnected.run();
                }
            }

            default ->
                    System.out.println("[Client] Unhandled message type: " + messageType);
        }
    }
}