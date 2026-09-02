package network.messages.lobby;

import network.messages.Message;

public class ChatMessageBroadcast extends Message {
    private final String senderName;
    private final String timestamp;
    private final String text;

    public ChatMessageBroadcast(String senderName, String timestamp, String text) {
        super("CHAT_MESSAGE");
        this.senderName = senderName;
        this.timestamp = timestamp;
        this.text = text;
    }

    public String getSenderName() { return senderName; }
    public String getTimestamp() { return timestamp; }
    public String getText() { return text; }
}