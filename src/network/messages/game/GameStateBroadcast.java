package network.messages.game;

import network.messages.Message;

public class GameStateBroadcast extends Message {
    private final String activePlayerId;
    private final int currentTurn;
    // در اینجا به جای کل مپ، یک رشته JSON فیلتر شده از مپ قرار می‌گیرد
    private final String filteredMapJson;

    public GameStateBroadcast(String activePlayerId, int currentTurn, String filteredMapJson) {
        super("GAME_STATE_UPDATE");
        this.activePlayerId = activePlayerId;
        this.currentTurn = currentTurn;
        this.filteredMapJson = filteredMapJson;
    }

    public String getActivePlayerId() { return activePlayerId; }
    public int getCurrentTurn() { return currentTurn; }
    public String getFilteredMapJson() { return filteredMapJson; }
}