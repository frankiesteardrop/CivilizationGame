package network.messages.lobby;

import network.messages.Message;

public class JoinLobbyRequest extends Message {

    private final String username;
    private final String password; // فیلد جدید برای احراز هویت

    public JoinLobbyRequest(String username, String password) {
        super("JOIN_LOBBY");
        this.username = username;
        this.password = password;
    }

    public String getUsername() { return username; }
    public String getPassword() { return password; }
}