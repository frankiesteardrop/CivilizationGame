package network.messages.game;

import network.messages.Message;


public class GameNotificationMessage extends Message {

    private final String text;

    public GameNotificationMessage(String text) {
        super("GAME_NOTIFICATION");
        this.text = text;
    }

    public String getText() { return text; }
}