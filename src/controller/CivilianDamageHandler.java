package controller;

import model.Unit;
import model.UnitType;
import java.util.List;

public class CivilianDamageHandler extends DamageHandler {
    @Override
    public void handleDamage(List<Unit> units, int damageAmount) {
        if (damageAmount <= 0) return;
        for (Unit u : units) {
            if ((u.getType() == UnitType.WORKER ||
                    u.getType() == UnitType.BUILDER ||
                    u.getType() == UnitType.EXPLORER ||
                    u.getType() == UnitType.BORDER_EXPANDER) && u.isAlive()) {

                u.takeDamage(1);
                damageAmount--;
                if (damageAmount == 0) return;
            }
        }
        if (next != null && damageAmount > 0) {
            next.handleDamage(units, damageAmount);
        }
    }
}