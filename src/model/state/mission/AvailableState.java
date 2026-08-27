package model.state.mission;

import model.GameMap;
import model.Tribe;
import model.TribeCamp;
import model.Unit;
import model.mission.Mission;

public class AvailableState implements MissionState {

    @Override
    public String getDisplayName() {
        return "Available";
    }

    @Override
    public boolean canAccept() {
        return true;
    }

    @Override
    public boolean canDeliver() {
        return false;
    }

    @Override
    public void handleTurn(Mission mission, Tribe tribe) {
    }

    @Override
    public void checkConditions(Mission mission, TribeCamp camp, GameMap map) {
    }

    @Override
    public boolean deliver(Mission mission, TribeCamp camp, GameMap map) {
        return false;
    }

    @Override
    public void onUnitKilled(Mission mission, TribeCamp camp, Unit unit, GameMap map) {
    }
}