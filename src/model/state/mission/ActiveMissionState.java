package model.state.mission;
import model.*;
import model.mission.Mission;

public class ActiveMissionState implements MissionState {
    @Override public String getDisplayName() { return "Active"; }

    @Override
    public void handleTurn(Mission mission, Tribe tribe) {
        mission.decrementTurn();
        if (mission.getTurnsRemaining() <= 0) {
            mission.setState(new CompletedFailedState("Failed"));
            tribe.addRelationship(-10);
            tribe.setMissionCooldown(5);
            GameEventDispatcher.fireNotification("❌ Mission for " + tribe.getType().getDisplayName() + " failed!");
        }
    }

    @Override
    public void checkConditions(Mission mission, TribeCamp camp, GameMap map) {
        if (mission.getGoal().isCompleted(map, camp)) {
            mission.setState(new ReadyMissionState());
            GameEventDispatcher.fireNotification("✅ Mission for " + camp.getTribe().getType().getDisplayName() + " is ready to deliver!");
        }
    }

    @Override public boolean deliver(Mission mission, TribeCamp camp, GameMap map) { return false; }

    @Override
    public void onUnitKilled(Mission mission, TribeCamp camp, Unit unit, GameMap map) {
        // اختصاصی برای قبیله جنگجو
        if (camp.getTribe().getType() == TribeType.WARRIOR) {
            Hex campHex = map.getHexOfBuilding(camp);
            if (campHex != null && map.getHexDistance(unit.getQ(), unit.getR(), campHex.getQ(), campHex.getR()) <= 5) {
                mission.addProgress(1);
                checkConditions(mission, camp, map);
            }
        }
    }
}