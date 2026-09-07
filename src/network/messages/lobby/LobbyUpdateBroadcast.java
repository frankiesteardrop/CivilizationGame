package network.messages.lobby;

import network.messages.Message;
import java.util.List;


public class LobbyUpdateBroadcast extends Message {

    private final List<LobbyPlayer> players;


    private final String selectedMapId;

    public LobbyUpdateBroadcast(List<LobbyPlayer> players, String selectedMapId) {
        super("LOBBY_UPDATE");
        this.players       = players;
        this.selectedMapId = selectedMapId;
    }

    public List<LobbyPlayer> getPlayers()      { return players; }
    public String            getSelectedMapId() { return selectedMapId; }
}