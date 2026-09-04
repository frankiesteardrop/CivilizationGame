package network.messages.lobby;

/**
 * Represents a player in the pre-game lobby.
 *
 * <p>Fields {@code id} and {@code createdAt} satisfy the spec requirement for
 * all server-managed entities to have a unique identifier and a creation timestamp.
 */
public class LobbyPlayer {

    private final String  id;
    private final String  username;
    private boolean       isReady;
    private boolean       isHost;

    /** Unix epoch milliseconds when this lobby entry was created. (B21) */
    private final long createdAt;

    public LobbyPlayer(String id, String username, boolean isHost) {
        this.id        = id;
        this.username  = username;
        this.isHost    = isHost;
        this.isReady   = false;
        this.createdAt = System.currentTimeMillis(); // (B21)
    }

    public String  getId()       { return id; }
    public String  getUsername() { return username; }
    public boolean isReady()     { return isReady; }
    public void    setReady(boolean ready) { isReady = ready; }
    public boolean isHost()      { return isHost; }
    public void    setHost(boolean host)   { isHost = host; }
    public long    getCreatedAt() { return createdAt; } // (B21)
}