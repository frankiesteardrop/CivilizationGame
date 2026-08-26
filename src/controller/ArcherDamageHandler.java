package controller;

import model.Unit;
import model.UnitType;
import java.util.List;
import java.util.stream.Collectors;

public class ArcherDamageHandler extends DamageHandler {
    @Override
    public void handleDamage(List<Unit> units, int damageAmount) {
        if (damageAmount <= 0) return;
        for (Unit u : units) {
            if (u.getType() == UnitType.ARCHER && u.isAlive()) {
                while (u.isAlive() && damageAmount > 0) {
                    u.takeDamage(1);
                    damageAmount--;
                }
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