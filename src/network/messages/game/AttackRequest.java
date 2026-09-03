package network.messages.game;

import network.messages.Message;

public class AttackRequest extends Message {
    private final int sourceQ;
    private final int sourceR;
    private final int targetQ;
    private final int targetR;

    public AttackRequest(int sourceQ, int sourceR, int targetQ, int targetR) {
        super("ATTACK_REQUEST");
        this.sourceQ = sourceQ;
        this.sourceR = sourceR;
        this.targetQ = targetQ;
        this.targetR = targetR;
    }

    public int getSourceQ() { return sourceQ; }
    public int getSourceR() { return sourceR; }
    public int getTargetQ() { return targetQ; }
    public int getTargetR() { return targetR; }
}