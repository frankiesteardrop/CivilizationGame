package network.messages;

public class ActionRequest extends Message {
    private final String playerId;
    private final String actionType; // e.g., "MOVE", "BUILD", "ATTACK"
    private final String payload;    // JSON string containing specific action details

    public ActionRequest(String playerId, String actionType, String payload) {
        super("ACTION_REQUEST");
        this.playerId = playerId;
        this.actionType = actionType;
        this.payload = payload;
    }

    public String getPlayerId() { return playerId; }
    public String getActionType() { return actionType; }
    public String getPayload() { return payload; }
}