package network.messages.lobby;

import network.messages.Message;

public class PlayerIdAssignedMessage extends Message {

    private final String assignedId;
    private final String jwtToken;

    public PlayerIdAssignedMessage(String assignedId, String jwtToken) {
        super("PLAYER_ID_ASSIGNED");
        this.assignedId = assignedId;
        this.jwtToken = jwtToken;
    }

    public String getAssignedId() { return assignedId; }
    public String getJwtToken() { return jwtToken; }
}