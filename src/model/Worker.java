package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        // [گام حل باگ ۱۳]: حذف اعداد هاردکد شده و ارسال نوع یونیت
        super(q, r, UnitType.WORKER);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    @Override
    public void resetAP() {
        if (!isAlive) return;
        super.resetAP();
        // [گام ۱ - اصلاح باگ اکشن‌پوینت کارگر]: اگر کارگر در شروع نوبت همچنان در سازه مستقر باشد،
        // هزینه حضور و کار در کارگاه در همان بدو نوبت از سقف AP او کسر می‌شود.
        if (isStationed) {
            consumeAP(GameConfig.WORKER_STATION_AP_COST);
        }
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