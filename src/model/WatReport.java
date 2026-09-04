package model;

import java.util.UUID;

/**
 * Records the details of a combat incident during another player's turn.
 *
 * <p>Created by {@link network.server.GameStateManager} when an attack
 * affects a player during their opponent's turn. Delivered to the affected
 * player at the start of their next turn as a "war report" per the spec.
 *
 * <p>Both {@code id} and {@code createdAt} satisfy the spec requirement for
 * all server-managed entities to have a unique identifier and a creation timestamp.
 */
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

    public WatReport(String attackerPlayerId, String attackerPlayerName,
                     int targetQ, int targetR,
                     int defenderUnitsDestroyed, int siegeDamageDealt,
                     boolean isSiegeAttack) {
        this.id                    = UUID.randomUUID().toString();
        this.attackerPlayerId      = attackerPlayerId;
        this.attackerPlayerName    = attackerPlayerName;
        this.targetQ               = targetQ;
        this.targetR               = targetR;
        this.defenderUnitsDestroyed = defenderUnitsDestroyed;
        this.siegeDamageDealt      = siegeDamageDealt;
        this.isSiegeAttack         = isSiegeAttack;
        this.createdAt             = System.currentTimeMillis();
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

    /**
     * Returns a human-readable summary of this war report for display in the UI.
     */
    public String toDisplayText() {
        if (isSiegeAttack) {
            return String.format(
                    "⚔️ [War Report] %s attacked your hex (%d,%d) and dealt %d siege damage!",
                    attackerPlayerName, targetQ, targetR, siegeDamageDealt);
        } else {
            return String.format(
                    "⚔️ [War Report] %s attacked your units at (%d,%d) — %d of your units were destroyed!",
                    attackerPlayerName, targetQ, targetR, defenderUnitsDestroyed);
        }
    }
}