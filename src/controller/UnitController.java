package controller;

import model.Hex;
import model.Unit;
import model.Worker;
import model.Building;
import model.BuildingType;

/**
 * کنترلر مدیریت اقدامات یونیت‌ها.
 * مسئول اعتبارسنجی و اجرای حرکت، استقرار و خروج یونیت‌ها.
 */
public class UnitController {

    /**
     * بررسی امکان حرکت یونیت به هکس هدف.
     *
     * شرایط لازم:
     * - یونیت زنده باشد
     * - هکس هدف null نباشد
     * - Worker stationed نباشد
     * - هکس هدف کشف‌شده (explored) باشد
     * - هکس هدف همسایه مستقیم یونیت باشد (فرمول Axial)
     * - AP کافی برای هزینه حرکت وجود داشته باشد
     */
    public boolean canMove(Unit unit, Hex targetHex) {
        if (unit == null || !unit.isAlive() || targetHex == null) return false;

        // Worker مستقر نمی‌تواند حرکت کند
        if (unit instanceof Worker && ((Worker) unit).isStationed()) return false;

        // نمی‌توان وارد هکس‌های ناشناخته (تاریک) شد
        if (!targetHex.isExplored()) return false;

        // بررسی همسایگی با فرمول استاندارد Axial Coordinates
        // در Axial Coordinates، همسایه‌های مستقیم یک هکس 6 جهت دارند
        // فرمول: max(|dq|, |dr|, |ds|) == 1 که ds = -dq - dr
        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr; // مؤلفه سوم در Cube Coordinates

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        // بررسی AP کافی برای هزینه حرکت بر اساس نوع زمین
        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    /**
     * بررسی امکان استقرار Worker در ساختمان هکس مشخص.
     *
     * شرایط لازم:
     * - Worker از قبل stationed نباشد
     * - AP کافی برای هزینه استقرار داشته باشد
     * - هکس ساختمان فعال داشته باشد (نه TownHall)
     * - ساختمان ظرفیت خالی داشته باشد
     */
    public boolean canStation(Worker worker, Hex hex) {
        if (worker == null || !worker.isAlive()) return false;
        if (worker.isStationed()) return false;
        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;

        // TownHall محل کار Worker نیست
        if (building.getType() == BuildingType.TOWN_HALL) return false;

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    /**
     * اجرای استقرار Worker در ساختمان.
     * پیش از فراخوانی این متد، باید canStation را بررسی کرده باشید.
     *
     * @return true اگر استقرار موفق بود
     */
    public boolean handleStation(Worker worker, Hex hex) {
        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (worker.isStationed()) return false;

        return worker.stationIn(building);
    }

    /**
     * خروج Worker از ساختمان.
     * خروج رایگان است و نیاز به AP ندارد — طبق داک.
     */
    public void handleEject(Worker worker) {
        if (worker != null && worker.isStationed()) {
            worker.eject();
        }
    }

    /**
     * بررسی امکان خروج Worker از ساختمان.
     * خروج فقط نیاز دارد Worker stationed باشد — AP لازم ندارد.
     */
    public boolean canEject(Worker worker) {
        return worker != null && worker.isAlive() && worker.isStationed();
    }
}