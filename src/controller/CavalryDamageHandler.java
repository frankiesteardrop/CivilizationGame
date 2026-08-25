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
                // اصلاح: تمرکز دمیج روی یک یونیت تا زمان مرگ (برای سواره‌نظام که ۲ جان دارد)
                while (u.isAlive() && damageAmount > 0) {
                    u.takeDamage(1);
                    damageAmount--;
                }
                if (damageAmount == 0) return;
            }
        }
        // اصلاح حیاتی: این بخش در کد شما جا افتاده بود که باعث شکستن الگو (Chain) می‌شد!
        if (next != null && damageAmount > 0) {
            next.handleDamage(units, damageAmount);
        }
    }
}