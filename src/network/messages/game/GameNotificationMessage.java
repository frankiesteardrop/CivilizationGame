package network.messages.game;

import network.messages.Message;

/**
 * Sent by the server to one or all clients to display a notification message
 * in the game UI (e.g., trade offer sent, crafting started, alliance requested).
 *
 * <p>The client routes this to {@link model.GameEventDispatcher#fireNotification(String)}
 * so it appears as an in-game toast notification.
 */
public class GameNotificationMessage extends Message {

    private final String text;

    public GameNotificationMessage(String text) {
        super("GAME_NOTIFICATION");
        this.text = text;
    }

    public String getText() { return text; }
}