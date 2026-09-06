package network.messages.lobby;

import network.messages.Message;

/**
 * Sent by the server to a specific client immediately after they join the lobby
 * to assign them their authoritative UUID.
 */
public class PlayerIdAssignedMessage extends Message {

    private final String assignedId;

    public PlayerIdAssignedMessage(String assignedId) {
        super("PLAYER_ID_ASSIGNED");
        this.assignedId = assignedId;
    }

    public String getAssignedId() {
        return assignedId;
    }
}