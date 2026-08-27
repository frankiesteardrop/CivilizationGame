package controller;

import model.Unit;
import model.UnitType;
import java.util.List;
import java.util.stream.Collectors;

public class CavalryDamageHandler extends DamageHandler {
    @Override
    public void handleDamage(List<Unit> units, int damageAmount) {
        if (damageAmount <= 0) return;
        for (Unit u : units) {
            if (u.getType() == UnitType.CAVALRY && u.isAlive()) {
                int damageToDeal = Math.min(u.getHp(), damageAmount);
                u.takeDamage(damageToDeal);
                damageAmount -= damageToDeal;
                if (damageAmount == 0) return;
            }
        }
        if (next != null && damageAmount > 0) {
            List<Unit> aliveUnits = units.stream().filter(Unit::isAlive).collect(Collectors.toList());
            if (!aliveUnits.isEmpty()) {
                next.handleDamage(aliveUnits, damageAmount);
            }
        }
    }
}