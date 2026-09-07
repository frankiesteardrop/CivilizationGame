package network.messages.game;

import network.messages.Message;
import java.util.Map;

public class GameStateBroadcast extends Message {
    private final String activePlayerId;
    private final String activePlayerName;
    private final int currentTurn;
    private final String filteredMapJson;
    private final Map<String, String[]> diplomacyStatuses;

    public GameStateBroadcast(String activePlayerId, String activePlayerName, int currentTurn,
                              String filteredMapJson, Map<String, String[]> diplomacyStatuses) {
        super("GAME_STATE_UPDATE");
        this.activePlayerId = activePlayerId;
        this.activePlayerName = activePlayerName;
        this.currentTurn = currentTurn;
        this.filteredMapJson = filteredMapJson;
        this.diplomacyStatuses = diplomacyStatuses;
    }

    public String getActivePlayerId() { return activePlayerId; }
    public String getActivePlayerName() { return activePlayerName; }
    public int getCurrentTurn() { return currentTurn; }
    public String getFilteredMapJson() { return filteredMapJson; }
    public Map<String, String[]> getDiplomacyStatuses() { return diplomacyStatuses; }
}