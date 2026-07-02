package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        // maxAP=2 (کمترین), foodConsumption=1 (کمترین), visionRadius=1
        super(q, r, 2, 1, 1);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    @Override
    public void resetAP() {
        if (!isAlive) return;
        super.resetAP();
    }

    public boolean stationIn(Building building) {
        if (isStationed) return false;
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;
        if (currentAP < GameConfig.WORKER_STATION_AP_COST) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;
        consumeAP(GameConfig.WORKER_STATION_AP_COST);
        GameEventDispatcher.fireUnitStateChanged(this);
        return true;
    }

    public void eject() {
        if (!isStationed || stationedBuilding == null) return;
        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;
        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public boolean isStationed() { return isStationed; }
    public Building getStationedBuilding() { return stationedBuilding; }
    public static int getStationApCost() { return GameConfig.WORKER_STATION_AP_COST; }
}