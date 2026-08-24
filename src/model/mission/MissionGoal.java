package model.mission;

import model.GameMap;
import model.Tribe;
import model.TribeCamp;

public interface MissionGoal {
    int getInitialTurns();
    boolean isCompleted(GameMap map, TribeCamp camp);
    void grantReward(GameMap map, Tribe tribe, TribeCamp camp);
    String getDescription(); // MVC Fix: توضیحات مأموریت در لایه Model کپسوله شد
}