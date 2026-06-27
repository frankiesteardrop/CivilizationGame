package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;

public class HUDPanel extends JPanel implements GameEventListener {
    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JLabel infoLabel;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(30, 32, 36));

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(70, 130, 180)),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        infoLabel = new JLabel();
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        add(infoLabel, BorderLayout.CENTER);

        JButton endTurnBtn = new JButton("End Turn");
        endTurnBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        endTurnBtn.setBackground(new Color(178, 34, 34));
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFocusPainted(false);
        endTurnBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        endTurnBtn.addActionListener(e -> handleEndTurn());
        add(endTurnBtn, BorderLayout.EAST);

        GameEventDispatcher.addListener(this);
        updateHUD();
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitKilled(Unit unit) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onProductionCompleted(String itemName) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onTurnEnded(int newTurn) {
        SwingUtilities.invokeLater(() -> {
            updateHUD();
            gamePanel.repaint();
        });
    }

    private void updateHUD() {
        GameMap map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();
        int max = inv.getMaxCapacity();

        String foodHtml = formatResource("Food", inv.getResourceAmount(ResourceType.FOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.FOOD));
        String woodHtml = formatResource("Wood", inv.getResourceAmount(ResourceType.WOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.WOOD));
        String stoneHtml = formatResource("Stone", inv.getResourceAmount(ResourceType.STONE), max, EconomyManager.calculateNetProduction(map, ResourceType.STONE));
        String ironHtml = formatResource("Iron", inv.getResourceAmount(ResourceType.IRON), max, EconomyManager.calculateNetProduction(map, ResourceType.IRON));

        // ۱. منطق نمایش صف تولید
        String queueHtml = " | &nbsp;<b>Queue:</b> ";
        TownHall.ProductionTask currentTask = map.getTownHall().getProductionQueue().peek();
        if (currentTask != null) {
            queueHtml += "<font color='#FFD700'>" + currentTask.getName() + " (" + currentTask.getTurnsRemaining() + "T)</font>";
        } else {
            queueHtml += "<font color='#808080'>Idle</font>";
        }

        // ۲. منطق آمار تفکیکی یونیت‌ها
        int e = 0, b = 0, w = 0, x = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) {
                if (u instanceof Explorer) e++;
                else if (u instanceof Builder) b++;
                else if (u instanceof Worker) w++;
                else if (u instanceof BorderExpander) x++;
            }
        }
        String unitBreakdown = String.format("<font size='3'> (E:%d B:%d W:%d X:%d)</font>", e, b, w, x);
        String unitHtml = " | &nbsp;<b>Units:</b> " + map.getAliveUnitsCount() + "/" + map.getUnitCap() + unitBreakdown;

        String turnHtml = " | &nbsp;<font color='#00FFFF'><b>Turn: " + map.getCurrentTurn() + "</b></font>";

        infoLabel.setText("<html>" + foodHtml + woodHtml + stoneHtml + ironHtml + queueHtml + unitHtml + turnHtml + "</html>");
    }

    private String formatResource(String name, int amount, int max, int net) {
        String netColor = net < 0 ? "#FF6347" : "#32CD32";
        String sign = net > 0 ? "+" : "";
        return String.format("<b>%s:</b> %d/%d (<font color='%s'>%s%d</font>) &nbsp;", name, amount, max, netColor, sign, net);
    }

    private void handleEndTurn() {
        mainController.getTurnController().tryEndTurn(this);
    }
}