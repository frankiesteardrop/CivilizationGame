package network.messages.lobby;

import network.messages.Message;

/**
 * Sent by the host client when they select a different map from the lobby UI.
 * The server validates that the sender is the host before accepting the change.
 */
public class SelectMapRequest extends Message {

    private final String mapId;

    public SelectMapRequest(String mapId) {
        super("SELECT_MAP");
        this.mapId = mapId;
    }

    public String getMapId() { return mapId; }
}