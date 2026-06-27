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

        // جراحی ریاضی گام نهم: استفاده از فرمول استاندارد جهانی برای تشخیص همسایگی در هکس‌های محوری
        // اگر دو هکس دقیقاً همسایه باشند، مجموع قدر مطلق تفاضل‌ها باید دقیقاً برابر 2 باشد.
        boolean isNeighbor = (Math.abs(dq) + Math.abs(dr) + Math.abs(dq + dr) == 2);

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