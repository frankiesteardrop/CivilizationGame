package model;

import java.util.Collections;
import java.util.List;

public final class CombatResult {

    private final int siegeDamage;
    private final int defenderUnitsDestroyed;
    private final int attackerUnitsDestroyed;

    private final List<Integer> attackerDice;
    private final List<Integer> defenderDice;

    public CombatResult(int siegeDamage,
                        int defenderUnitsDestroyed,
                        int attackerUnitsDestroyed,
                        List<Integer> attackerDice,
                        List<Integer> defenderDice) {
        this.siegeDamage            = siegeDamage;
        this.defenderUnitsDestroyed = defenderUnitsDestroyed;
        this.attackerUnitsDestroyed = attackerUnitsDestroyed;

        this.attackerDice = (attackerDice != null) ? Collections.unmodifiableList(attackerDice) : Collections.emptyList();
        this.defenderDice = (defenderDice != null) ? Collections.unmodifiableList(defenderDice) : Collections.emptyList();
    }

    public int getSiegeDamage() { return siegeDamage; }
    public int getDefenderUnitsDestroyed() { return defenderUnitsDestroyed; }

    public int getAttackerUnitsDestroyed() { return attackerUnitsDestroyed; }

    public List<Integer> getAttackerDice() { return attackerDice; }
    public List<Integer> getDefenderDice() { return defenderDice; }

    public boolean isSiegeAttack() { return siegeDamage > 0; }

    @Override
    public String toString() {
        if (isSiegeAttack()) {
            return "CombatResult[SIEGE: damage=" + siegeDamage + "]";
        }
        return "CombatResult[UNIT: defKilled=" + defenderUnitsDestroyed
                + ", atkKilled=" + attackerUnitsDestroyed
                + ", atkDice=" + attackerDice
                + ", defDice=" + defenderDice + "]";
    }
}