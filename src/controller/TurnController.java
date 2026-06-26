package controller;

import model.GameMap;
import model.Unit;
import model.Worker;
import javax.swing.JOptionPane;
import java.awt.Component;

public class TurnController {
    private final GameMap gameMap;

    public TurnController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean tryEndTurn(Component parentView) {
        boolean hasIdle = false;
        for (Unit u : gameMap.getUnits()) {
            if (u.isAlive() && u.getCurrentAP() > 0) {
                if (u instanceof Worker && ((Worker) u).isStationed()) continue;
                hasIdle = true; break;
            }
        }

        if (hasIdle) {
            int confirm = JOptionPane.showConfirmDialog(parentView,
                    "You have idle units with remaining AP. Are you sure you want to end the turn?",
                    "Idle Units Warning",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return false;
            }
        }

        gameMap.nextTurn();
        return true;
    }
}