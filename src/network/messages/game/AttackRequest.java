package network.messages.game;

import network.messages.Message;
import java.util.List;

public class AttackRequest extends Message {

    private final List<String> attackerIds;
    private final int targetQ;
    private final int targetR;

    public AttackRequest(List<String> attackerIds, int targetQ, int targetR) {
        super("ATTACK_REQUEST");
        this.attackerIds = attackerIds;
        this.targetQ = targetQ;
        this.targetR = targetR;
    }

    public List<String> getAttackerIds() { return attackerIds; }
    public int getTargetQ() { return targetQ; }
    public int getTargetR() { return targetR; }
}