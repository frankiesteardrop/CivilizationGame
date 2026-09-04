package network.messages.game;

import network.messages.Message;

/**
 * Sent by the client when they accept or reject an alliance request.
 * The server validates that a pending request from {@code requesterId} to the
 * sender actually exists before applying the response.
 */
public class AllianceResponseRequest extends Message {

    /** The ID of the player who originally sent the alliance request. */
    private final String requesterId;

    /** True = accept the alliance; false = reject it. */
    private final boolean accepted;

    public AllianceResponseRequest(String requesterId, boolean accepted) {
        super("ALLIANCE_RESPONSE");
        this.requesterId = requesterId;
        this.accepted    = accepted;
    }

    public String  getRequesterId() { return requesterId; }
    public boolean isAccepted()     { return accepted; }
}