package network.messages.game;

import network.messages.Message;

public class AllianceResponseRequest extends Message {

    private final String requesterId;

    private final boolean accepted;

    public AllianceResponseRequest(String requesterId, boolean accepted) {
        super("ALLIANCE_RESPONSE");
        this.requesterId = requesterId;
        this.accepted    = accepted;
    }

    public String  getRequesterId() { return requesterId; }
    public boolean isAccepted()     { return accepted; }
}