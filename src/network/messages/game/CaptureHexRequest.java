package network.messages.game;

import network.messages.Message;

public class CaptureHexRequest extends Message {

    private final String unitId;
    private final int targetQ;
    private final int targetR;

    public CaptureHexRequest(
            String unitId,
            int targetQ,
            int targetR) {

        super("CAPTURE_HEX");

        this.unitId = unitId;
        this.targetQ = targetQ;
        this.targetR = targetR;
    }

    public String getUnitId() {
        return unitId;
    }

    public int getTargetQ() {
        return targetQ;
    }

    public int getTargetR() {
        return targetR;
    }
}