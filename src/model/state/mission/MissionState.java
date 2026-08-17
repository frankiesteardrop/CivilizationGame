package model.state.mission;
import model.GameMap;
import model.Tribe;
import model.TribeCamp;
import model.Unit;
import model.mission.Mission;

public interface MissionState {
    String getDisplayName();
    void handleTurn(Mission mission, Tribe tribe);
    void checkConditions(Mission mission, TribeCamp camp, GameMap map);
    boolean deliver(Mission mission, TribeCamp camp, GameMap map);
    void onUnitKilled(Mission mission, TribeCamp camp, Unit unit, GameMap map);
}