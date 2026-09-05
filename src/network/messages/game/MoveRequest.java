package network.messages.game;

import network.messages.Message;

public class MoveRequest extends Message {
    private final int srcQ, srcR;
    private final int tgtQ, tgtR;

    public MoveRequest(int srcQ, int srcR, int tgtQ, int tgtR) {
        super("MOVE_REQUEST");
        this.srcQ = srcQ;
        this.srcR = srcR;
        this.tgtQ = tgtQ;
        this.tgtR = tgtR;
    }

    public int getSrcQ() { return srcQ; }
    public int getSrcR() { return srcR; }
    public int getTgtQ() { return tgtQ; }
    public int getTgtR() { return tgtR; }
}