package network.messages.lobby;
import network.messages.Message;

// درخواست ارسال پیام متنی در چت
public class ChatSendRequest extends Message {
    private final String text;
    public ChatSendRequest(String text) {
        super("CHAT_SEND");
        this.text = text;
    }
    public String getText() { return text; }
}