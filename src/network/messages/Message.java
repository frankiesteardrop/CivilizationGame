package network.messages;

import java.util.UUID;

public abstract class Message {
    protected final String id;
    protected final long createdAt;
    protected final String type;

    protected String token;

    public Message(String type) {
        this.id = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
        this.type = type;
        this.token = null;
    }

    public String getId() { return id; }
    public long getCreatedAt() { return createdAt; }
    public String getType() { return type; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
}