package model.state.tribe;
import model.*;
import java.util.List;

public class EnemyState implements TribeState {
    @Override public String getName() { return "Enemy"; }
    @Override public boolean canTrade() { return false; }
    @Override public boolean canReceiveGift() { return false; }
    @Override public boolean canFormAlliance() { return false; }
    @Override public boolean canRequestPeace() { return true; }

    @Override public boolean isHostile() { return true; }
    @Override public boolean canDeclareWar() { return false; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        int currentCount = camp.getAndIncrementGuardCounter();
        if (currentCount > 0 && currentCount % 3 == 0) {
            long currentGuards = map.getUnits().stream()
                    .filter(u -> u.isAlive()
                            // شمارش هر دو نوع نیروی نظامی برای جلوگیری از تولید بیش از حد
                            && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER)
                            && u.isEnemy()
                            && map.getHexDistance(campHex.getQ(), campHex.getR(), u.getQ(), u.getR()) <= 3)
                    .count();

            // اصلاح گام دوم: تعیین سقف مجاز تولید گارد بر اساس نوع قبیله
            int maxGuards = (tribe.getType() == TribeType.WARRIOR) ? 5 : 3;

            // اصلاح گام دوم: بررسی رسیدن به سقف داینامیک به جای صفر بودن
            if (currentGuards < maxGuards) {
                deferredActions.add(() -> {
                    Hex spawnHex = map.findNearbyEmptyHex(campHex.getQ(), campHex.getR(), 3);
                    if (spawnHex != null) {

                        // هوش مصنوعی ارتش متنوع برای قبیله جنگجو
                        UnitType guardType = UnitType.SWORDSMAN;
                        if (tribe.getType() == TribeType.WARRIOR) {
                            guardType = map.getRandom().nextBoolean() ? UnitType.SWORDSMAN : UnitType.ARCHER;
                        }

                        Unit guard = UnitFactory.createUnit(guardType, spawnHex.getQ(), spawnHex.getR());
                        guard.setEnemy(true);
                        map.addUnit(guard);

                        if (campHex.isVisible()) {
                            GameEventDispatcher.fireNotification("⚠️ " + tribe.getType().getDisplayName() + " tribe mobilized a guard to defend its camp!");
                        }
                    }
                });
            }
        }
    }
}