package controller;

import model.GameMap;
import model.Hex;
import model.Unit;
import model.Worker;
import model.Building;
import model.BuildingType;
import model.BorderExpander;
import model.GameConfig;

/**
 * کنترلر مدیریت اقدامات یونیت‌ها.
 * مسئول اعتبارسنجی و اجرای حرکت، استقرار، خروج یونیت‌ها و منطق انتخاب.
 */
public class UnitController {

    // انتقال وضعیت‌های انتخاب یونیت از لایه گرافیک به لایه کنترلر
    private Hex lastClickedHex = null;
    private int unitCycleIndex = 0;

    public boolean canMove(Unit unit, Hex targetHex) {
        if (unit == null || !unit.isAlive() || targetHex == null) return false;
        if (unit instanceof Worker && ((Worker) unit).isStationed()) return false;

        // هات‌فیکس: خط محدودکننده کاوش پاک شد تا یونیت‌ها بتوانند در تاریکی قدم بگذارند و نقشه کشف شود.

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr;

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    /**
     * اجرای قطعی حرکت یونیت پس از اتمام انیمیشن گرافیکی.
     * اصلاح گام چهارم: انتقال کنترل مه‌جنگ به لایه Controller برای حفظ یکپارچگی MVC
     */
    public void executeMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || targetHex == null || map == null) return;

        int cost = targetHex.getTerrainType().getMovementCost();
        unit.moveTo(targetHex.getQ(), targetHex.getR(), cost);
        map.updateFogOfWar();
    }

    public boolean canStation(Worker worker, Hex hex) {
        if (worker == null || !worker.isAlive()) return false;
        if (worker.isStationed()) return false;
        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (building.getType() == BuildingType.TOWN_HALL) return false;

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    public boolean handleStation(Worker worker, Hex hex) {
        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (worker.isStationed()) return false;

        return worker.stationIn(building);
    }

    public void handleEject(Worker worker) {
        if (worker != null && worker.isStationed()) {
            worker.eject();
        }
    }

    public boolean canEject(Worker worker) {
        return worker != null && worker.isAlive() && worker.isStationed();
    }

    // اصلاح گام ۳ (باگ ۷): اضافه شدن هندلر BorderExpander جهت حفظ کامل الگوی MVC
    public boolean handleExpandBorder(BorderExpander expander, GameMap map) {
        if (!expander.canExpand(map)) return false;
        expander.consumeAP(GameConfig.EXPAND_AP_COST);
        map.expandBorderAt(expander.getQ(), expander.getR());
        map.updateFogOfWar();
        expander.kill();
        return true;
    }

    /**
     * منطق هوشمند چرخش و انتخاب یونیت (انتقال یافته از لایه View جهت رعایت MVC).
     */
    public Unit selectUnitAt(Hex hex, GameMap map) {
        java.util.List<Unit> unitsOnHex = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR())
                .collect(java.util.stream.Collectors.toList());

        if (unitsOnHex.isEmpty()) {
            lastClickedHex = null; // ریست کردن وضعیت در صورت کلیک روی جای خالی
            return null;
        }

        if (hex == lastClickedHex) {
            unitCycleIndex = (unitCycleIndex + 1) % unitsOnHex.size();
        } else {
            unitCycleIndex = 0;
            lastClickedHex = hex;
        }
        return unitsOnHex.get(unitCycleIndex);
    }
}