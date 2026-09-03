package network.messages.game;

import network.messages.Message;

public class EndTurnRequest extends Message {
    public EndTurnRequest() {
        super("END_TURN");
    }
}