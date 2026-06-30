package model;

public class Worker extends Unit {
    private static final int STATION_AP_COST = 1;
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
        // اصلاح گام اول: کارگر مستقر نیز AP کامل دریافت می‌کند (جهت پرداخت هزینه غذا)
        // اما به دلیل isStationed بودن، اجازه حرکت نخواهد داشت.
        super.resetAP();
    }

    public boolean stationIn(Building building) {
        if (isStationed) return false;
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;
        if (currentAP < STATION_AP_COST) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;
        consumeAP(STATION_AP_COST);
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
    public static int getStationApCost() { return STATION_AP_COST; }
}