package model.state.mission;

import model.*;
import model.mission.Mission;

public class ActiveMissionState implements MissionState {

    @Override public String getDisplayName() { return "Active"; }

    @Override public boolean canAccept() { return false; }
    @Override public boolean canDeliver() { return false; }

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
        if (camp.getTribe().getType() != TribeType.WARRIOR) return;

        if (!unit.isEnemy() && unit.getType() != UnitType.BEAR) return;

        Hex campHex = map.getHexOfBuilding(camp);
        if (campHex == null) return;

        int distToKill = map.getHexDistance(unit.getQ(), unit.getR(), campHex.getQ(), campHex.getR());
        if (distToKill > 5) return;

        boolean playerCausedKill = map.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && !u.isEnemy()
                        && u.getType() != UnitType.BEAR
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY)
                        && map.getHexDistance(u.getQ(), u.getR(), unit.getQ(), unit.getR()) <= u.getAttackRange());

        if (!playerCausedKill) {
            return;
        }

        mission.addProgress(1);
        GameEventDispatcher.fireNotification("⚔️ Warrior Mission: " + mission.getProgress() + "/2 enemies defeated near camp.");

        if (mission.getProgress() >= 2) {
            mission.setState(new ReadyMissionState());
            GameEventDispatcher.fireNotification("✅ Mission for Warrior Tribe is ready to deliver!");
        }
    }
}