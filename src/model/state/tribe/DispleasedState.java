package model.state.tribe;

import model.*;
import java.util.List;

public class DispleasedState implements TribeState {

    @Override public String  getName()           { return "Displeased"; }
    @Override public boolean canTrade()          { return false; }
    @Override public boolean canReceiveGift()    { return true;  }
    @Override public boolean canFormAlliance()   { return false; }
    @Override public boolean canRequestPeace()   { return false; }
    @Override public boolean isHostile()         { return false; }
    @Override public boolean canDeclareWar()     { return true;  }

    /**
     * I8: اصلاح شعاع چک حضور نظامی از ۳ به ۱.
     *
     * طبق spec: «محدوده‌ی ممنوعه شامل هکس کمپ و شش هکس اطراف آن است»
     * = camp hex + ۶ مجاور = شعاع ۱.
     *
     * همچنین اضافه شد: فیلتر !u.isEnemy() تا گاردهای خود قبیله (که isEnemy=true دارند)
     * trigger نکنند.
     */
    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex,
                                    GameMap map, List<Runnable> deferredActions) {
        // I8: شعاع اصلاح‌شده از 3 به 1 + فیلتر !u.isEnemy()
        boolean hasMilitaryNearby = map.getUnits().stream().anyMatch(u ->
                u.isAlive()
                        && !u.isEnemy()
                        && (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(campHex.getQ(), campHex.getR(),
                        u.getQ(), u.getR()) <= 1);

        if (hasMilitaryNearby) {
            camp.incrementDispleasedMilTurns();
            // هر ۲ ترن حضور نظامی → کاهش رابطه
            if (camp.getDispleasedMilitaryTurns() % 2 == 0) {
                tribe.addRelationship(-1);
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "😠 " + tribe.getType().getDisplayName()
                                    + " is alarmed by military presence near their camp! (-1 relation)");
                }
            }
        } else {
            camp.resetDispleasedMilTurns();
        }
    }
}