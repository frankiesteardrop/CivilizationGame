package model.state.mission;

import model.GameMap;
import model.Tribe;
import model.TribeCamp;
import model.Unit;
import model.mission.Mission;

public class CompletedFailedState implements MissionState {
    private final String finalState;

    public CompletedFailedState(String finalState) {
        this.finalState = finalState;
    }

    @Override public String getDisplayName() { return finalState; }

    @Override public boolean canAccept() { return false; }
    @Override public boolean canDeliver() { return false; }

    @Override public void handleTurn(Mission mission, Tribe tribe) {}
    @Override public void checkConditions(Mission m, TribeCamp c, GameMap map) {}
    @Override public boolean deliver(Mission m, TribeCamp c, GameMap map) { return false; }
    @Override public void onUnitKilled(Mission m, TribeCamp c, Unit u, GameMap map) {}
}