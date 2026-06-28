package controller;

import model.Hex;
import model.Unit;
import model.Worker;
import model.Building;

public class UnitController {

    /**
     * بررسی امکان حرکت یونیت به هکس مقصد.
     * شرایط:
     * - یونیت زنده و غیر-stationed باشد
     * - هکس مقصد explored شده باشد (Fog of War)
     * - هکس مقصد همسایه مستقیم باشد
     * - AP کافی داشته باشد
     */
    public boolean canMove(Unit unit, Hex targetHex) {
        if (!unit.isAlive()) return false;

        // Worker مستقر نمی‌تواند حرکت کند
        if (unit instanceof Worker && ((Worker) unit).isStationed()) {
            return false;
        }

        // طبق داک: نمی‌توان وارد هکس‌های ناشناخته شد
        if (!targetHex.isExplored()) {
            return false;
        }

        // بررسی همسایگی با فرمول استاندارد هکس
        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        boolean isNeighbor = (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr) == 2);

        if (!isNeighbor) return false;

        // بررسی AP کافی برای هزینه حرکت این نوع زمین
        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    /**
     * استقرار Worker در سازه موجود در هکس.
     * هزینه ۱ AP داخل Worker.stationIn() کسر می‌شود.
     */
    public boolean handleStation(Worker worker, Hex hex) {
        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (worker.isStationed()) return false;

        return worker.stationIn(building);
    }

    /**
     * خروج Worker از سازه.
     * AP بازگردانده نمی‌شود (طبق داک).
     */
    public void handleEject(Worker worker) {
        if (worker.isStationed()) {
            worker.eject();
        }
    }

    /**
     * بررسی اینکه آیا Worker می‌تواند در سازه این هکس مستقر شود.
     * برای غیرفعال کردن دکمه در UI استفاده می‌شود.
     */
    public boolean canStation(Worker worker, Hex hex) {
        if (worker.isStationed()) return false;
        if (worker.getCurrentAP() < 1) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;

        return true;
    }
}