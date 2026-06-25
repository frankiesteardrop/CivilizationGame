package view;

import model.*;

import javax.swing.*;
import java.awt.*;

public class HUDPanel extends JPanel {
    private final GameMap gameMap;
    private final GamePanel gamePanel;
    private final JLabel infoLabel;

    public HUDPanel(GameMap gameMap, GamePanel gamePanel) {
        this.gameMap = gameMap;
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

        // تایمر برای آپدیت زنده اطلاعات
        Timer uiTimer = new Timer(100, e -> updateHUD());
        uiTimer.start();
    }

    private void updateHUD() {
        Inventory inv = gameMap.getTownHall().getInventory();
        int max = inv.getMaxCapacity();

        String foodHtml = formatResource("Food", inv.getResourceAmount(ResourceType.FOOD), max, EconomyManager.calculateNetProduction(gameMap, ResourceType.FOOD));
        String woodHtml = formatResource("Wood", inv.getResourceAmount(ResourceType.WOOD), max, EconomyManager.calculateNetProduction(gameMap, ResourceType.WOOD));
        String stoneHtml = formatResource("Stone", inv.getResourceAmount(ResourceType.STONE), max, EconomyManager.calculateNetProduction(gameMap, ResourceType.STONE));
        String ironHtml = formatResource("Iron", inv.getResourceAmount(ResourceType.IRON), max, EconomyManager.calculateNetProduction(gameMap, ResourceType.IRON));

        String unitHtml = " | Units: " + gameMap.getAliveUnitsCount() + "/" + gameMap.getUnitCap();
        String turnHtml = " | <b>Turn: " + gameMap.getCurrentTurn() + "</b>";

        infoLabel.setText("<html>" + foodHtml + woodHtml + stoneHtml + ironHtml + unitHtml + turnHtml + "</html>");
    }

    private String formatResource(String name, int amount, int max, int net) {
        String netColor = net < 0 ? "red" : "#00FF00"; // قرمز برای منفی، سبز برای مثبت
        String sign = net > 0 ? "+" : "";
        return String.format("%s: %d/%d (<font color='%s'>%s%d</font>) &nbsp;&nbsp;", name, amount, max, netColor, sign, net);
    }

    private void handleEndTurn() {
        // چک کردن یونیت‌های بی‌کار طبق داک پروژه
        boolean hasIdle = false;
        for (Unit u : gameMap.getUnits()) {
            if (u.isAlive() && u.getCurrentAP() > 0) {
                // کارگری که مستقر است استثناست (چون وظیفه‌اش در حال انجام است)
                if (u instanceof Worker && ((Worker) u).isStationed()) continue;
                hasIdle = true;
                break;
            }
        }

        if (hasIdle) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "You have idle units with remaining AP. Are you sure you want to end the turn?",
                    "Idle Units Warning",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (confirm != JOptionPane.YES_OPTION) {
                return; // لغو پایان نوبت
            }
        }

        // پردازش اقتصاد و رفتن به نوبت بعد
        gameMap.nextTurn();
        gamePanel.repaint();
    }
}