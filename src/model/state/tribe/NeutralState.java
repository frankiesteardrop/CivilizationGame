package model.state.tribe;

import model.*;
import java.util.List;

public class NeutralState implements TribeState {

    @Override
    public String getName() { return "Neutral"; }

    @Override public boolean canTrade()          { return false; }
    @Override public boolean canReceiveGift()    { return true;  }
    @Override public boolean canFormAlliance()   { return false; }
    @Override public boolean canRequestPeace()   { return false; }
    @Override public boolean isHostile()         { return false; }
    @Override public boolean canDeclareWar()     { return true;  }

    /**
     * I7: پیاده‌سازی رفتار قبیله خنثی هنگام ورود نیروی نظامی به محدوده ممنوعه.
     *
     * محدوده ممنوعه طبق spec: هکس کمپ + شش هکس اطراف آن = شعاع ۱.
     *
     * منطق:
     * - ترن اول با نیروی نظامی در محدوده: نمایش هشدار (بدون کاهش رابطه)
     * - ترن‌های بعدی با نیروی نظامی هنوز در محدوده: کاهش رابطه ۱ واحد
     * - هیچ نیروی نظامی در محدوده نیست: reset کردن counter
     */
    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex,
                                    GameMap map, List<Runnable> deferredActions) {
        // I7: بررسی حضور نیروی نظامی بازیکن در محدوده ممنوعه (شعاع ۱)
        // فقط یونیت‌های خودی (!isEnemy) چک می‌شوند — نه گاردهای خود قبیله
        boolean hasMilitaryInRestrictedZone = map.getUnits().stream().anyMatch(u ->
                u.isAlive()
                        && !u.isEnemy()
                        && (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(campHex.getQ(), campHex.getR(),
                        u.getQ(), u.getR()) <= 1);

        if (hasMilitaryInRestrictedZone) {
            camp.incrementNeutralMilTurns();

            if (camp.getNeutralMilitaryTurns() == 1) {
                // ترن اول ورود: فقط هشدار — بدون کاهش رابطه (طبق spec)
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "⚠️ " + tribe.getType().getDisplayName()
                                    + " Tribe is watching your military presence near their camp! "
                                    + "Withdraw or relations will suffer.");
                }
            } else {
                // ترن‌های بعدی: کاهش رابطه (طبق spec: «رابطه با قبیله کاهش می‌یابد»)
                tribe.addRelationship(-1);
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "😠 " + tribe.getType().getDisplayName()
                                    + " Tribe is displeased by your continued military presence near their camp! (-1 relation)");
                }
            }
        } else {
            // هیچ نیروی نظامی در محدوده ممنوعه نیست — reset counter
            camp.resetNeutralMilTurns();
        }
    }
}