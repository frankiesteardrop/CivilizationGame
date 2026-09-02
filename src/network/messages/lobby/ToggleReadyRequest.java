package network.messages.lobby;
import network.messages.Message;

// درخواست تغییر وضعیت آمادگی
public class ToggleReadyRequest extends Message {
    public ToggleReadyRequest() {
        super("TOGGLE_READY");
    }
}