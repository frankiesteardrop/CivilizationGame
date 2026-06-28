package controller;

import model.Hex;
import model.Unit;
import model.Worker;
import model.Building;

public class UnitController {

    public boolean canMove(Unit unit, Hex targetHex) {
        // گارد امنیتی برای جلوگیری از NullPointerException
        if (!unit.isAlive() || targetHex == null) return false;

        // Worker مستقر نمی‌تواند حرکت کند
        if (unit instanceof Worker && ((Worker) unit).isStationed()) {
            return false;
        }

        // گارد امنیتی حیاتی: نمی‌توان وارد هکس‌های ناشناخته (تاریک) شد
        if (!targetHex.isExplored()) {
            return false;
        }

        // بررسی همسایگی با فرمول استاندارد هکس
        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        boolean isNeighbor = (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr) == 2);

        if (!isNeighbor) return false;

        // بررسی AP کافی برای هزینه حرکت
        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    public boolean handleStation(Worker worker, Hex hex) {
        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (worker.isStationed()) return false;

        return worker.stationIn(building);
    }

    public void handleEject(Worker worker) {
        if (worker.isStationed()) {
            worker.eject();
        }
    }

    public boolean canStation(Worker worker, Hex hex) {
        if (worker.isStationed() || worker.getCurrentAP() < 1) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        return building.getStationedWorkers() < building.getMaxWorkers();
    }
}