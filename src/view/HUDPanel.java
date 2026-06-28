package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HUDPanel extends JPanel implements GameEventListener {
    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JLabel infoLabel;
    private final JButton endTurnBtn;
    private boolean confirmIdleMode = false;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(25, 28, 33)); // تم دارک و شیک

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 4, 0, new Color(41, 128, 185)), // خط آبی زیرین
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        infoLabel = new JLabel();
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 15));
        add(infoLabel, BorderLayout.CENTER);

        endTurnBtn = new JButton("END TURN");
        endTurnBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        endTurnBtn.setBackground(new Color(192, 57, 43));
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFocusPainted(false);
        endTurnBtn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        endTurnBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // افکت Hover (هاور) زیبا روی دکمه
        endTurnBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { endTurnBtn.setBackground(endTurnBtn.getBackground().brighter()); }
            @Override
            public void mouseExited(MouseEvent e) { updateButtonColor(); }
        });

        endTurnBtn.addActionListener(e -> handleEndTurn());
        add(endTurnBtn, BorderLayout.EAST);

        GameEventDispatcher.addListener(this);
        updateHUD();
    }

    private void updateButtonColor() {
        if (confirmIdleMode) endTurnBtn.setBackground(new Color(230, 126, 34)); // نارنجی هشدار
        else endTurnBtn.setBackground(new Color(192, 57, 43)); // قرمز تیره
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitKilled(Unit unit) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onProductionCompleted(String itemName) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onTurnEnded(int newTurn) {
        SwingUtilities.invokeLater(() -> {
            confirmIdleMode = false;
            endTurnBtn.setText("END TURN");
            updateButtonColor();
            updateHUD();
            gamePanel.repaint();
        });
    }

    private void updateHUD() {
        GameMap map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();
        int max = inv.getMaxCapacity();
        int netFood = EconomyManager.calculateNetProduction(map, ResourceType.FOOD);

        // استفاده از استایل‌دهی پیشرفته HTML و ایموجی‌ها برای نظم‌دهی چشم‌نواز منابع
        String foodHtml = formatResource("🍔 Food", inv.getResourceAmount(ResourceType.FOOD), max, netFood);
        String woodHtml = formatResource("🪵 Wood", inv.getResourceAmount(ResourceType.WOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.WOOD));
        String stoneHtml = formatResource("🪨 Stone", inv.getResourceAmount(ResourceType.STONE), max, EconomyManager.calculateNetProduction(map, ResourceType.STONE));
        String ironHtml = formatResource("⚙️ Iron", inv.getResourceAmount(ResourceType.IRON), max, EconomyManager.calculateNetProduction(map, ResourceType.IRON));

        boolean isStarving = inv.getResourceAmount(ResourceType.FOOD) == 0 && netFood < 0;

        String queueHtml = "&nbsp;&nbsp;<span style='color:#7f8c8d'>|</span>&nbsp;&nbsp;<b>Queue:</b> ";
        TownHall.ProductionTask currentTask = map.getTownHall().getProductionQueue().peek();
        if (currentTask != null) {
            String queueColor = isStarving ? "#e74c3c" : "#f1c40f";
            String freezeText = isStarving ? " (FROZEN)" : "";
            queueHtml += "<span style='color:" + queueColor + "'>" + currentTask.getName() + " (" + currentTask.getTurnsRemaining() + "T)" + freezeText + "</span>";
        } else {
            queueHtml += "<span style='color:#95a5a6'>Idle</span>";
        }

        int e = 0, b = 0, w = 0, x = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) {
                if (u instanceof Explorer) e++;
                else if (u instanceof Builder) b++;
                else if (u instanceof Worker) w++;
                else if (u instanceof BorderExpander) x++;
            }
        }
        String unitBreakdown = String.format("<span style='font-size:11px; color:#bdc3c7;'> (E:%d B:%d W:%d X:%d)</span>", e, b, w, x);
        String unitHtml = "&nbsp;&nbsp;<span style='color:#7f8c8d'>|</span>&nbsp;&nbsp;<b>Pop:</b> " + map.getAliveUnitsCount() + "/" + map.getUnitCap() + unitBreakdown;

        String turnHtml = "&nbsp;&nbsp;<span style='color:#7f8c8d'>|</span>&nbsp;&nbsp;<span style='color:#3498db'><b>Turn: " + map.getCurrentTurn() + "</b></span>";

        String starvationWarning = isStarving ? "&nbsp;&nbsp;<span style='background-color:#e74c3c; color:white; padding:3px 6px;'><b>⚠️ STARVATION</b></span>" : "";

        // چیدمان با فاصله یکسان برای جلوگیری از شلوغی
        infoLabel.setText("<html><body style='margin:0; padding:0;'>" + foodHtml + "&nbsp;&nbsp;" + woodHtml + "&nbsp;&nbsp;" + stoneHtml + "&nbsp;&nbsp;" + ironHtml + queueHtml + unitHtml + turnHtml + starvationWarning + "</body></html>");
    }

    private String formatResource(String name, int amount, int max, int net) {
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71"; // قرمز و سبز متریال
        String sign = net > 0 ? "+" : "";
        return String.format("<b>%s:</b> %d<span style='color:#7f8c8d'>/%d</span> (<span style='color:%s'>%s%d</span>)", name, amount, max, netColor, sign, net);
    }

    private void handleEndTurn() {
        if (!confirmIdleMode && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("IDLE UNITS! CONFIRM");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
    }
}