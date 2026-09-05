package network.messages.game;

import network.messages.Message;

public class BuildRequest extends Message {
    private final int unitQ, unitR;
    private final String actionType; // "BUILD", "ROAD", "WALL", "DESTROY", "STATION", "EJECT"
    private final String structureType;
    private final int hexQ, hexR;
    private final int dir;

    public BuildRequest(int unitQ, int unitR, String actionType, String structureType, int hexQ, int hexR, int dir) {
        super("BUILD_REQUEST");
        this.unitQ = unitQ;
        this.unitR = unitR;
        this.actionType = actionType;
        this.structureType = structureType;
        this.hexQ = hexQ;
        this.hexR = hexR;
        this.dir = dir;
    }

    public int getUnitQ() { return unitQ; }
    public int getUnitR() { return unitR; }
    public String getActionType() { return actionType; }
    public String getStructureType() { return structureType; }
    public int getHexQ() { return hexQ; }
    public int getHexR() { return hexR; }
    public int getDir() { return dir; }
}