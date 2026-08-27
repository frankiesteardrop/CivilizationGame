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

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex,
                                    GameMap map, List<Runnable> deferredActions) {

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
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "⚠️ " + tribe.getType().getDisplayName()
                                    + " Tribe is watching your military presence near their camp! "
                                    + "Withdraw or relations will suffer.");
                }
            } else {
                tribe.addRelationship(-1);
                if (campHex.isVisible()) {
                    GameEventDispatcher.fireNotification(
                            "😠 " + tribe.getType().getDisplayName()
                                    + " Tribe is displeased by your continued military presence near their camp! (-1 relation)");
                }
            }
        } else {
            camp.resetNeutralMilTurns();
        }
    }
}