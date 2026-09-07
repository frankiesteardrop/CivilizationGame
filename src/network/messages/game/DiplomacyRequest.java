package network.messages.game;

import network.messages.Message;

public class DiplomacyRequest extends Message {
    private final String targetPlayerId;
    private final String action;

    public DiplomacyRequest(String targetPlayerId, String action) {
        super("DIPLOMACY_ACTION");
        this.targetPlayerId = targetPlayerId;
        this.action = action;
    }

    public String getTargetPlayerId() { return targetPlayerId; }
    public String getAction() { return action; }
}