package model;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;

public class WatReport {

    private final String id;
    private final String attackerPlayerId;
    private final String attackerPlayerName;
    private final int    targetQ;
    private final int    targetR;
    private final int    defenderUnitsDestroyed;
    private final int    siegeDamageDealt;
    private final boolean isSiegeAttack;
    private final long   createdAt;

    // 🔴 فیلدهای جدید برای شفافیت جنگ
    private final List<Integer> attackerDice;
    private final List<Integer> defenderDice;
    private final int attackerLosses;

    public WatReport(String attackerPlayerId, String attackerPlayerName,
                     int targetQ, int targetR,
                     int defenderUnitsDestroyed, int attackerLosses,
                     int siegeDamageDealt, boolean isSiegeAttack,
                     List<Integer> attackerDice, List<Integer> defenderDice) {
        this.id                    = UUID.randomUUID().toString();
        this.attackerPlayerId      = attackerPlayerId;
        this.attackerPlayerName    = attackerPlayerName;
        this.targetQ               = targetQ;
        this.targetR               = targetR;
        this.defenderUnitsDestroyed = defenderUnitsDestroyed;
        this.attackerLosses        = attackerLosses;
        this.siegeDamageDealt      = siegeDamageDealt;
        this.isSiegeAttack         = isSiegeAttack;
        this.createdAt             = System.currentTimeMillis();

        this.attackerDice = (attackerDice != null) ? new ArrayList<>(attackerDice) : new ArrayList<>();
        this.defenderDice = (defenderDice != null) ? new ArrayList<>(defenderDice) : new ArrayList<>();
    }

    public String  getId()                     { return id; }
    public String  getAttackerPlayerId()        { return attackerPlayerId; }
    public String  getAttackerPlayerName()      { return attackerPlayerName; }
    public int     getTargetQ()                { return targetQ; }
    public int     getTargetR()                { return targetR; }
    public int     getDefenderUnitsDestroyed()  { return defenderUnitsDestroyed; }
    public int     getSiegeDamageDealt()        { return siegeDamageDealt; }
    public boolean isSiegeAttack()             { return isSiegeAttack; }
    public long    getCreatedAt()              { return createdAt; }

    public String toDisplayText() {
        StringBuilder sb = new StringBuilder();
        sb.append("⚔️ [War Report] ").append(attackerPlayerName);
        sb.append(" attacked hex (").append(targetQ).append(",").append(targetR).append(")\n");
        if (!isSiegeAttack) {
            sb.append("Attacker dice: ").append(attackerDice).append("\n");
            sb.append("Defender dice: ").append(defenderDice).append("\n");
            sb.append("Your units lost: ").append(defenderUnitsDestroyed).append("\n");
            sb.append("Attacker units lost: ").append(attackerLosses);
        } else {
            sb.append("Siege damage dealt to your buildings: ").append(siegeDamageDealt);
        }
        return sb.toString();
    }
}