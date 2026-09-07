package network.messages.lobby;
import network.messages.Message;

public class ToggleReadyRequest extends Message {
    public ToggleReadyRequest() {
        super("TOGGLE_READY");
    }
}