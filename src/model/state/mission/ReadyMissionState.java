package model.state.mission;

import model.GameMap;
import model.Tribe;
import model.TribeCamp;
import model.TribeType;
import model.Unit;
import model.mission.Mission;

public class ReadyMissionState implements MissionState {

    @Override public String getDisplayName() { return "Ready to Deliver"; }

    @Override public boolean canAccept() { return false; }
    @Override public boolean canDeliver() { return true; }

    @Override
    public void handleTurn(Mission mission, Tribe tribe) {
        mission.decrementTurn();
        if (mission.getTurnsRemaining() <= 0) {
            mission.setState(new CompletedFailedState("Failed"));
            tribe.addRelationship(-10);
            tribe.setMissionCooldown(5);
        }
    }

    @Override
    public void checkConditions(Mission mission, TribeCamp camp, GameMap map) {
        if (camp.getTribe().getType() == TribeType.WARRIOR) {
            if (mission.getProgress() < 2) {
                mission.setState(new ActiveMissionState());
            }
            return;
        }

        if (!mission.getGoal().isCompleted(map, camp)) {
            mission.setState(new ActiveMissionState());
        }
    }

    @Override
    public boolean deliver(Mission mission, TribeCamp camp, GameMap map) {
        boolean isCompleted;
        if (camp.getTribe().getType() == TribeType.WARRIOR) {
            isCompleted = (mission.getProgress() >= 2);
        } else {
            isCompleted = mission.getGoal().isCompleted(map, camp);
        }

        if (isCompleted) {
            mission.getGoal().grantReward(map, camp.getTribe(), camp);
            mission.setState(new CompletedFailedState("Completed"));
            return true;
        }
        return false;
    }

    @Override public void onUnitKilled(Mission m, TribeCamp c, Unit u, GameMap map) {}
}