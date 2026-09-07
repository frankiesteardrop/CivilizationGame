package network.messages.lobby;


public class LobbyPlayer {

    private final String  id;
    private final String  username;
    private boolean       isReady;
    private boolean       isHost;

    private final long createdAt;

    public LobbyPlayer(String id, String username, boolean isHost) {
        this.id        = id;
        this.username  = username;
        this.isHost    = isHost;
        this.isReady   = false;
        this.createdAt = System.currentTimeMillis();
    }

    public String  getId()       { return id; }
    public String  getUsername() { return username; }
    public boolean isReady()     { return isReady; }
    public void    setReady(boolean ready) { isReady = ready; }
    public boolean isHost()      { return isHost; }
    public void    setHost(boolean host)   { isHost = host; }
    public long    getCreatedAt() { return createdAt; }
}