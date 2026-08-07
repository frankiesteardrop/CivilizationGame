package controller;

import model.Unit;
import model.UnitType;
import java.util.List;

public class CavalryDamageHandler extends DamageHandler {
    @Override
    public void handleDamage(List<Unit> units, int damageAmount) {
        if (damageAmount <= 0) return;
        for (Unit u : units) {
            if (u.getType() == UnitType.CAVALRY && u.isAlive()) {
                u.takeDamage(1);
                damageAmount--;
                if (damageAmount == 0) return;
            }
        }
    }
}