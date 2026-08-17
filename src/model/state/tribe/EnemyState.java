package model.state.tribe;
import model.*;
import java.util.List;

public class EnemyState implements TribeState {
    @Override public String getName() { return "Enemy"; }
    @Override public boolean canTrade() { return false; }
    @Override public boolean canReceiveGift() { return false; }
    @Override public boolean canFormAlliance() { return false; }
    @Override public boolean canRequestPeace() { return true; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        int currentCount = camp.getAndIncrementGuardCounter();
        if (currentCount > 0 && currentCount % 3 == 0) {
            int maxGuards = (tribe.getType() == TribeType.WARRIOR) ? 5 : 3;
            long currentGuards = map.getUnits().stream()
                    .filter(u -> u.isAlive() && u.getType() == UnitType.SWORDSMAN
                            && map.getHexDistance(campHex.getQ(), campHex.getR(), u.getQ(), u.getR()) <= 3)
                    .count();

            if (currentGuards < maxGuards) {
                deferredActions.add(() -> {
                    Hex spawnHex = map.findNearbyEmptyHex(campHex.getQ(), campHex.getR(), 3);
                    if (spawnHex != null) {
                        map.addUnit(UnitFactory.createUnit(UnitType.SWORDSMAN, spawnHex.getQ(), spawnHex.getR()));
                        if (campHex.isVisible()) {
                            GameEventDispatcher.fireNotification("⚠️ " + tribe.getType().getDisplayName() + " tribe mobilized guards!");
                        }
                    }
                });
            }
        }
    }
}