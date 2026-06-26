package controller;

import model.Hex;
import model.Unit;
import model.Worker;
import javax.swing.JOptionPane;
import java.awt.Component;

public class UnitController {

    public boolean canMove(Unit unit, Hex targetHex, Component parentView) {
        if (unit instanceof Worker && ((Worker) unit).isStationed()) {
            JOptionPane.showMessageDialog(parentView, "Worker is stationed. Leave facility first!");
            return false;
        }

        int dq = targetHex.getQ() - unit.getQ();
        int dr = targetHex.getR() - unit.getR();
        boolean isNeighbor = (Math.abs(dq) <= 1 && Math.abs(dr) <= 1 && Math.abs(dq + dr) <= 1) && !(dq == 0 && dr == 0);

        if (!isNeighbor) {
            JOptionPane.showMessageDialog(parentView, "Movement is only allowed to adjacent hexes!");
            return false;
        }

        int cost = targetHex.getTerrainType().getMovementCost();
        if (unit.getCurrentAP() < cost) {
            JOptionPane.showMessageDialog(parentView, "Not enough Action Points (AP)! Need " + cost);
            return false;
        }
        return true;
    }

    public void handleStation(Worker worker, Hex hex) {
        if (hex.getBuilding() != null && !worker.isStationed()) {
            worker.stationIn(hex.getBuilding());
        }
    }

    public void handleEject(Worker worker) {
        if (worker.isStationed()) {
            worker.eject();
        }
    }
}