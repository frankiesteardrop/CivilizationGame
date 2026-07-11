package controller;

import model.GameMap;
import model.Hex;
import model.Unit;
import model.Worker;
import model.Building;
import model.BuildingType;
import model.BorderExpander;
import model.GameConfig;
import model.GameEventDispatcher;
import model.ResourceType;

public class UnitController {

    private Hex lastClickedHex = null;
    private int unitCycleIndex = 0;

    public boolean canMove(Unit unit, Hex targetHex) {
        if (unit == null || !unit.isAlive() || targetHex == null) return false;
        if (unit instanceof Worker && ((Worker) unit).isStationed()) return false;

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        int ds = -dq - dr;

        boolean isNeighbor = (Math.max(Math.max(Math.abs(dq), Math.abs(dr)), Math.abs(ds)) == 1);
        if (!isNeighbor) return false;

        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    public void executeMove(Unit unit, Hex targetHex, GameMap map) {
        if (unit == null || targetHex == null || map == null) return;

        // گارد دوم: جلوگیری از باگ تداخلِ کلیک‌های همزمان در حین انیمیشن
        if (!canMove(unit, targetHex)) return;

        int cost = targetHex.getTerrainType().getMovementCost();
        unit.moveTo(targetHex.getQ(), targetHex.getR(), cost);
        map.updateFogOfWar();
    }

    public boolean canStation(Worker worker, Hex hex) {
        if (worker == null || !worker.isAlive()) return false;
        if (worker.isStationed()) return false;

        if (worker.getQ() != hex.getQ() || worker.getR() != hex.getR()) return false;

        if (worker.getCurrentAP() < Worker.getStationApCost()) return false;

        Building building = hex.getBuilding();
        if (building == null || building.isDestroyed()) return false;
        if (building.getType() == BuildingType.TOWN_HALL) return false;

        ResourceType producedRes = building.getType().getProducedResource();
        if (producedRes != ResourceType.NONE && !hex.hasResource(producedRes)) {
            return false;
        }

        return building.getStationedWorkers() < building.getMaxWorkers();
    }

    public boolean handleStation(Worker worker, Hex hex) {
        if (!canStation(worker, hex)) return false;

        Building building = hex.getBuilding();
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

    public boolean handleExpandBorder(BorderExpander expander, GameMap map) {
        if (!expander.canExpand(map)) return false;

        int q = expander.getQ();
        int r = expander.getR();

        expander.consumeAP(GameConfig.EXPAND_AP_COST);
        map.expandBorderAt(q, r);
        map.updateFogOfWar();
        expander.kill();

        GameEventDispatcher.fireBorderExpanded(q, r);

        return true;
    }

    public Unit selectUnitAt(Hex hex, GameMap map) {
        java.util.List<Unit> unitsOnHex = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR())
                .collect(java.util.stream.Collectors.toList());

        if (unitsOnHex.isEmpty()) {
            lastClickedHex = null;
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