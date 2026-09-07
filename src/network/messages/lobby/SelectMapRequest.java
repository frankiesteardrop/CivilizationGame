package network.messages.lobby;

import network.messages.Message;


public class SelectMapRequest extends Message {

    private final String mapId;

    public SelectMapRequest(String mapId) {
        super("SELECT_MAP");
        this.mapId = mapId;
    }

    public String getMapId() { return mapId; }
}