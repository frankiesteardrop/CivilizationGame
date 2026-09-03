package network.messages.lobby;

import network.messages.Message;

/**
 * Sent by the client immediately after connecting to introduce itself to the lobby.
 * The server uses the provided username to create a LobbyPlayer entry.
 */
public class JoinLobbyRequest extends Message {

    private final String username;

    public JoinLobbyRequest(String username) {
        super("JOIN_LOBBY");
        this.username = username;
    }

    public String getUsername() { return username; }
}