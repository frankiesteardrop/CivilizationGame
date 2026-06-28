package controller;

import model.Hex;
import model.Unit;
import model.Worker;

public class UnitController {

    public boolean canMove(Unit unit, Hex targetHex) {
        if (unit instanceof Worker && ((Worker) unit).isStationed()) {
            return false;
        }

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();

        // بررسی همسایگی با فرمول استاندارد
        boolean isNeighbor = (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr) == 2);

        if (!isNeighbor) {
            return false;
        }

        int cost = targetHex.getTerrainType().getMovementCost();
        return unit.getCurrentAP() >= cost;
    }

    public boolean handleStation(Worker worker, Hex hex) {
        if (hex.getBuilding() != null && !worker.isStationed()) {
            return worker.stationIn(hex.getBuilding());
        }
        return false;
    }

    public void handleEject(Worker worker) {
        if (worker.isStationed()) {
            worker.eject();
        }
    }
}