package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        super(q, r, 2, 1, 1);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    public boolean isStationed() { return isStationed; }
    public Building getStationedBuilding() { return stationedBuilding; }

    @Override
    public void resetAP() {
        if (!isAlive) return;
        if (isStationed) {
            currentAP = 0;
        } else {
            currentAP = maxAP;
        }
    }

    public boolean stationIn(Building building) {
        if (isStationed || currentAP < 1 || building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;

        consumeAP(1);

        // اطلاع به گرافیک که کارگر مستقر شد
        GameEventDispatcher.fireUnitStateChanged(this);

        return true;
    }

    public void eject() {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // اطلاع به گرافیک که کارگر آزاد شد (چه دستی، چه اتوماتیک به خاطر اتمام منبع)
        GameEventDispatcher.fireUnitStateChanged(this);
    }
}