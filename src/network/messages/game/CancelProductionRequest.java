package network.messages.game;

import network.messages.Message;

public class CancelProductionRequest extends Message {
    public CancelProductionRequest() {
        super("CANCEL_PRODUCTION");
    }
}