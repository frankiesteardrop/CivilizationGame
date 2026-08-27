package model.state.tribe;
import model.*;
import model.mission.Mission;
import java.util.List;

public class FriendlyState implements TribeState {
    @Override public String getName() { return "Friendly"; }
    @Override public boolean canTrade() { return true; }
    @Override public boolean canReceiveGift() { return true; }
    @Override public boolean canFormAlliance() { return false; }
    @Override public boolean canRequestPeace() { return false; }

    @Override public boolean isHostile() { return false; }
    @Override public boolean canDeclareWar() { return true; }

    @Override
    public void executeTurnBehavior(Tribe tribe, TribeCamp camp, Hex campHex, GameMap map, List<Runnable> deferredActions) {
        if (tribe.getMissionCooldown() > 0) return;
        Mission m = tribe.getMission();
        boolean needsNew = (m == null || m.getState().getDisplayName().equals("Completed") || m.getState().getDisplayName().equals("Failed") || m.getState().getDisplayName().equals("Cancelled"));

        if (needsNew) {
            int counter = camp.getAndIncrementMissionCounter();
            if (counter > 0 && counter % 5 == 0) {
                tribe.setMission(new Mission(tribe.getType().getMissionGoal()));
                GameEventDispatcher.fireNotification("📜 " + tribe.getType().getDisplayName() + " has a new mission!");
            }
        }
    }
}