package network.messages;

import java.util.UUID;

public abstract class Message {
    protected final String id;
    protected final long createdAt;
    protected final String type;

    public Message(String type) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.type = type;
    }

    public String getId() { return id; }
    public long getCreatedAt() { return createdAt; }
    public String getType() { return type; }
}