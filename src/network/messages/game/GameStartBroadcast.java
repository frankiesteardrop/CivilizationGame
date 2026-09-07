package network.messages.game;

import network.messages.Message;


public class GameStartBroadcast extends Message {

    public GameStartBroadcast() {
        super("GAME_START_BROADCAST");
    }
}