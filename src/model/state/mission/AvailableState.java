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
        // در این وضعیت، بازیکن اجازه دارد مأموریت را قبول کند
        return true;
    }

    @Override
    public boolean canDeliver() {
        return false;
    }

    @Override
    public void handleTurn(Mission mission, Tribe tribe) {
        // تا زمانی که مأموریت اکسپت (Accept) نشده، ترن از آن کم نمی‌شود
    }

    @Override
    public void checkConditions(Mission mission, TribeCamp camp, GameMap map) {
        // نیازی به چک کردن شرایط نیست چون هنوز مأموریت آغاز نشده است
    }

    @Override
    public boolean deliver(Mission mission, TribeCamp camp, GameMap map) {
        return false;
    }

    @Override
    public void onUnitKilled(Mission mission, TribeCamp camp, Unit unit, GameMap map) {
        // هیچ اثری ندارد
    }
}