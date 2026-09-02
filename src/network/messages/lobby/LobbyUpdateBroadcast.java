package network.messages.lobby;

import network.messages.Message;
import java.util.List;

public class LobbyUpdateBroadcast extends Message {
    private final List<LobbyPlayer> players;

    public LobbyUpdateBroadcast(List<LobbyPlayer> players) {
        super("LOBBY_UPDATE");
        this.players = players;
    }

    public List<LobbyPlayer> getPlayers() { return players; }
}