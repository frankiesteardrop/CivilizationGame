package view;

import controller.GameController;
import model.*;
import javax.swing.*;
import java.awt.*;

public class HUDPanel extends JPanel implements GameEventListener {
    private final GameController gameController;
    private final GamePanel gamePanel;
    private final JLabel infoLabel;

    public HUDPanel(GameController gameController, GamePanel gamePanel) {
        this.gameController = gameController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(40, 40, 40));
        setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        infoLabel = new JLabel();
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setFont(new Font("Arial", Font.BOLD, 14));
        add(infoLabel, BorderLayout.CENTER);

        JButton endTurnBtn = new JButton("End Turn");
        endTurnBtn.setFont(new Font("Arial", Font.BOLD, 14));
        endTurnBtn.setBackground(new Color(139, 0, 0));
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFocusPainted(false);
        endTurnBtn.addActionListener(e -> handleEndTurn());
        add(endTurnBtn, BorderLayout.EAST);

        // ثبت این پنل به عنوان شنونده رویدادهای مدل
        GameEventDispatcher.addListener(this);
        updateHUD(); // آپدیت اولیه
    }

    // متدهای اینترفیس Observer که فقط زمان تغییر واقعی فراخوانی می‌شوند
    @Override public void onResourceChanged(ResourceType type, int newAmount) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitKilled(Unit unit) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onProductionCompleted(String itemName) { SwingUtilities.invokeLater(this::updateHUD); }

    private void updateHUD() {
        GameMap map = gameController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();
        int max = inv.getMaxCapacity();

        String foodHtml = formatResource("Food", inv.getResourceAmount(ResourceType.FOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.FOOD));
        String woodHtml = formatResource("Wood", inv.getResourceAmount(ResourceType.WOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.WOOD));
        String stoneHtml = formatResource("Stone", inv.getResourceAmount(ResourceType.STONE), max, EconomyManager.calculateNetProduction(map, ResourceType.STONE));
        String ironHtml = formatResource("Iron", inv.getResourceAmount(ResourceType.IRON), max, EconomyManager.calculateNetProduction(map, ResourceType.IRON));

        String unitHtml = " | Units: " + map.getAliveUnitsCount() + "/" + map.getUnitCap();
        String turnHtml = " | <b>Turn: " + map.getCurrentTurn() + "</b>";

        infoLabel.setText("<html>" + foodHtml + woodHtml + stoneHtml + ironHtml + unitHtml + turnHtml + "</html>");
    }

    private String formatResource(String name, int amount, int max, int net) {
        String netColor = net < 0 ? "red" : "#00FF00";
        String sign = net > 0 ? "+" : "";
        return String.format("%s: %d/%d (<font color='%s'>%s%d</font>) &nbsp;&nbsp;", name, amount, max, netColor, sign, net);
    }

    private void handleEndTurn() {
        boolean hasIdle = false;
        for (Unit u : gameController.getGameMap().getUnits()) {
            if (u.isAlive() && u.getCurrentAP() > 0) {
                if (u instanceof Worker && ((Worker) u).isStationed()) continue;
                hasIdle = true; break;
            }
        }

        if (hasIdle) {
            int confirm = JOptionPane.showConfirmDialog(this, "You have idle units. End turn?", "Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) return;
        }

        gameController.endTurn();
        updateHUD(); // آپدیت دستی برای تغییر شماره نوبت
        gamePanel.repaint();
    }
}