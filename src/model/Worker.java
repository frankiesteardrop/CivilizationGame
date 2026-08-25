package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        super(q, r, UnitType.WORKER);
        this.isStationed = false;
        this.stationedBuilding = null;
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

    public void restoreStation(Building building) {
        this.isStationed = true;
        this.stationedBuilding = building;
    }

    public void eject(GameMap map) {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // اصلاح حیاتی: استرداد AP مصرف شده برای استقرار طبق داک فاز اول
        this.currentAP = Math.min(this.maxAP, this.currentAP + GameConfig.WORKER_STATION_AP_COST);

        if (map != null) {
            Hex currentHex = map.getHexAt(this.q, this.r);

            boolean isOccupied = map.getUnits().stream()
                    .anyMatch(u -> u.isAlive() && u != this && u.getQ() == this.q && u.getR() == this.r);

            if (isOccupied || currentHex == null || currentHex.getTerrainType() == TerrainType.SEA || currentHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) {
                Hex safeHex = map.findNearbyEmptyHex(this.q, this.r, 3);

                if (safeHex != null) {
                    this.q = safeHex.getQ();
                    this.r = safeHex.getR();
                } else {
                    this.kill();
                    return;
                }
            }
        }

        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public void eject() {
        eject(null);
    }

    @Override
    public void kill() {
        if (isStationed) eject();
        super.kill();
    }

    public boolean isStationed() { return isStationed; }
    public Building getStationedBuilding() { return stationedBuilding; }
    public static int getStationApCost() { return GameConfig.WORKER_STATION_AP_COST; }
}