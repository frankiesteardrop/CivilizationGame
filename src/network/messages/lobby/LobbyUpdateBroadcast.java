package network.messages.lobby;

import network.messages.Message;
import java.util.List;

/**
 * Broadcast from server to all lobby clients whenever the lobby state changes.
 * Now includes the currently selected map name so the UI can display it.
 */
public class LobbyUpdateBroadcast extends Message {

    private final List<LobbyPlayer> players;

    /**
     * The ID of the map currently selected by the host.
     * Null if no selection has been made yet (use default).
     */
    private final String selectedMapId;

    public LobbyUpdateBroadcast(List<LobbyPlayer> players, String selectedMapId) {
        super("LOBBY_UPDATE");
        this.players       = players;
        this.selectedMapId = selectedMapId;
    }

    public List<LobbyPlayer> getPlayers()      { return players; }
    public String            getSelectedMapId() { return selectedMapId; }
}