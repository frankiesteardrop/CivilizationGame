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

    // رفع باگ 18: اورلود کردن متد eject برای دریافت مپ و خروج از هکسِ در حال تخریب
    public void eject(GameMap map) {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // فرار از آوار: پیدا کردن نزدیک‌ترین هکس خالی و معتبر (نه دریا، نه کوهستان)
        if (map != null) {
            Hex currentHex = map.getHexAt(this.q, this.r);
            if (currentHex != null && (currentHex.getBuilding() == null || currentHex.getBuilding().isDestroyed())) {
                boolean relocated = false;
                for (int i = 0; i < 6; i++) {
                    Hex n = map.getNeighbor(currentHex, i);
                    if (n != null && n.getTerrainType() != TerrainType.SEA && n.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                        // چک کردن اینکه یونیتی روی همسایه نباشد (ساده‌سازی)
                        boolean hasUnit = map.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == n.getQ() && u.getR() == n.getR());
                        if (!hasUnit) {
                            this.q = n.getQ();
                            this.r = n.getR();
                            relocated = true;
                            break;
                        }
                    }
                }
                // اگر هکس خالی پیدا نشد، در همان جا می‌ماند
            }
        }

        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public void eject() {
        eject(null); // سازگاری به عقب برای جاهایی که نیازی به تغییر موقعیت ندارند
    }

    @Override
    public void kill() {
        if (isStationed) {
            eject();
        }
        super.kill();
    }

    public boolean isStationed() {
        return isStationed;
    }

    public Building getStationedBuilding() {
        return stationedBuilding;
    }

    public static int getStationApCost() {
        return GameConfig.WORKER_STATION_AP_COST;
    }
}