package model.state.tribe;
import model.*;
import java.util.List;

public class DispleasedState implements TribeState {
    @Override public String getName() { return "Displeased"; }
    @Override public boolean canTrade() { return false; }
    @Override public boolean canReceiveGift() { return true; }
    @Override public boolean canFormAlliance() { return false; }
    @Override public boolean canRequestPeace() { return false; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        boolean hasMilitaryNearby = map.getUnits().stream().anyMatch(u -> u.isAlive()
                && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY)
                && map.getHexDistance(campHex.getQ(), campHex.getR(), u.getQ(), u.getR()) <= 3);

        if (hasMilitaryNearby) {
            camp.incrementDispleasedMilTurns();
            if (camp.getDispleasedMilitaryTurns() % 2 == 0) {
                tribe.addRelationship(-1);
                if (campHex.isVisible()) GameEventDispatcher.fireNotification("😠 " + tribe.getType().getDisplayName() + " is alarmed by military presence!");
            }
        } else {
            camp.resetDispleasedMilTurns();
        }
    }
}