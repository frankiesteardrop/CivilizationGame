package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * پنل HUD بازی — نمایش همیشه‌نمایان اطلاعات امپراتوری.
 */
public class HUDPanel extends JPanel implements GameEventListener {

    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JPanel infoContainer;
    private final JButton endTurnBtn;

    private boolean confirmIdleMode = false;
    private boolean isStarving = false;
    private boolean starvationAlertShown = false;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(25, 28, 33));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 4, 0, new Color(41, 128, 185)),
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        infoContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        infoContainer.setOpaque(false);
        add(infoContainer, BorderLayout.CENTER);

        endTurnBtn = buildEndTurnButton();
        add(endTurnBtn, BorderLayout.EAST);

        GameEventDispatcher.addListener(this);
        updateHUD();
    }

    private JButton buildEndTurnButton() {
        JButton btn = new JButton("END TURN");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(new Color(192, 57, 43));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);

        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                btn.setBackground(btn.getBackground().brighter());
            }
            @Override public void mouseExited(MouseEvent e) {
                updateButtonColor();
            }
        });

        btn.addActionListener(e -> handleEndTurn());
        return btn;
    }

    private void updateButtonColor() {
        endTurnBtn.setBackground(confirmIdleMode
                ? new Color(230, 126, 34)
                : new Color(192, 57, 43));
    }

    /**
     * [گام ۳ - اصلاح باگ UI]: ریست کردن وضعیت دکمه پایان نوبت به حالت عادی.
     */
    private void resetEndTurnButton() {
        if (confirmIdleMode) {
            confirmIdleMode = false;
            endTurnBtn.setText("END TURN");
            updateButtonColor();
        }
    }

    @Override
    public void onResourceChanged(ResourceType type, int newAmount) {
        SwingUtilities.invokeLater(this::updateHUD);
    }

    @Override
    public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {
        SwingUtilities.invokeLater(() -> {
            // [گام ۳]: هرگونه حرکت یونیت اخطار بی‌کاری را ریست می‌کند
            resetEndTurnButton();
            updateHUD();
        });
    }

    @Override
    public void onUnitKilled(Unit unit) {
        SwingUtilities.invokeLater(() -> {
            resetEndTurnButton();
            updateHUD();
        });
    }

    @Override
    public void onProductionCompleted(String itemName) {
        SwingUtilities.invokeLater(() -> {
            updateHUD();
            showProductionNotification(itemName);
        });
    }

    @Override
    public void onTurnEnded(int newTurn) {
        SwingUtilities.invokeLater(() -> {
            resetEndTurnButton();
            updateHUD();
            gamePanel.repaint();
        });
    }

    @Override
    public void onStarvationChanged(boolean starving) {
        SwingUtilities.invokeLater(() -> {
            boolean wasStarving = this.isStarving;
            this.isStarving = starving;
            updateHUD();

            if (starving && !wasStarving && !starvationAlertShown) {
                starvationAlertShown = true;
                showStarvationAlert();
            }

            if (!starving) {
                starvationAlertShown = false;
            }
        });
    }

    @Override
    public void onUnitStateChanged(Unit unit) {
        SwingUtilities.invokeLater(() -> {
            // [گام ۳]: استقرار یا خروج کارگر، اخطار بی‌کاری را ریست می‌کند
            resetEndTurnButton();
            updateHUD();
            gamePanel.repaint();
        });
    }

    @Override
    public void onBuildingConstructed(Hex hex) {
        SwingUtilities.invokeLater(() -> {
            updateHUD();
            gamePanel.repaint();
        });
    }

    @Override
    public void onBuildingDestroyed(Hex hex) {
        SwingUtilities.invokeLater(() -> {
            updateHUD();
            gamePanel.repaint();
        });
    }

    // [اصلاح گام ۶]: واکنش نشان دادن به توسعه مرز و ری‌پینت کردن نقشه
    @Override
    public void onBorderExpanded(int centerQ, int centerR) {
        SwingUtilities.invokeLater(() -> {
            resetEndTurnButton();
            updateHUD();
            gamePanel.repaint();
        });
    }

    private void updateHUD() {
        // [گام ۳]: اگر دکمه در حالت تایید است اما دیگر هیچ یونیت بی‌کاری وجود ندارد، خودکار ریست شود
        if (confirmIdleMode && !mainController.getTurnController().hasIdleUnits()) {
            resetEndTurnButton();
        }

        infoContainer.removeAll();

        GameMap   map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();

        int maxFood  = inv.getCapacity(ResourceType.FOOD);
        int maxWood  = inv.getCapacity(ResourceType.WOOD);
        int maxStone = inv.getCapacity(ResourceType.STONE);
        int maxIron  = inv.getCapacity(ResourceType.IRON);

        int netFood  = EconomyManager.calculateNetProduction(map, ResourceType.FOOD);
        int netWood  = EconomyManager.calculateNetProduction(map, ResourceType.WOOD);
        int netStone = EconomyManager.calculateNetProduction(map, ResourceType.STONE);
        int netIron  = EconomyManager.calculateNetProduction(map, ResourceType.IRON);

        infoContainer.add(createResourceCard(
                "🍔 Food",
                inv.getResourceAmount(ResourceType.FOOD), maxFood,  netFood,
                new Color(46, 204, 113)));
        infoContainer.add(createResourceCard(
                "🪵 Wood",
                inv.getResourceAmount(ResourceType.WOOD), maxWood,  netWood,
                new Color(211, 84, 0)));
        infoContainer.add(createResourceCard(
                "🪨 Stone",
                inv.getResourceAmount(ResourceType.STONE), maxStone, netStone,
                new Color(149, 165, 166)));
        infoContainer.add(createResourceCard(
                "⚙️ Iron",
                inv.getResourceAmount(ResourceType.IRON), maxIron,  netIron,
                new Color(243, 156, 18)));

        TownHall.ProductionTask currentTask =
                map.getTownHall().getProductionQueue().peek();
        String queueText;
        Color  queueColor;

        if (currentTask != null) {
            if (isStarving) {
                queueText  = currentTask.getName()
                        + " (" + currentTask.getTurnsRemaining() + "T)"
                        + " <span style='color:#e74c3c;'>❄️ FROZEN</span>";
                queueColor = new Color(231, 76, 60);
            } else {
                queueText  = currentTask.getName()
                        + " (" + currentTask.getTurnsRemaining() + "T)";
                queueColor = new Color(241, 196, 15);
            }
        } else {
            queueText  = "<span style='color:#7f8c8d;'>Idle</span>";
            queueColor = new Color(127, 140, 141);
        }
        infoContainer.add(createCard("🏗️ Queue", queueText, queueColor, false));

        long expCount   = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Explorer).count();
        long buildCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Builder).count();
        long workCount  = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Worker).count();
        long expndCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof BorderExpander).count();

        String unitText = map.getAliveUnitsCount() + "/" + map.getUnitCap()
                + " <span style='font-size:10px; color:#bdc3c7;'>"
                + "(E:" + expCount
                + " B:" + buildCount
                + " W:" + workCount
                + " X:" + expndCount + ")</span>";
        infoContainer.add(createCard(
                "👥 Pop", unitText, new Color(52, 152, 219), false));

        infoContainer.add(createCard(
                "⏳ Turn",
                String.valueOf(map.getCurrentTurn()),
                new Color(155, 89, 182), false));

        if (isStarving) {
            infoContainer.add(createStarvationCard());
        }

        infoContainer.revalidate();
        infoContainer.repaint();
    }

    private JPanel createResourceCard(String title, int amount, int max,
                                      int net, Color accentColor) {
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71";
        String sign     = net > 0 ? "+" : "";
        String valueText = amount
                + "<span style='color:#7f8c8d'>/" + max + "</span> "
                + "(<span style='color:" + netColor + "'>"
                + sign + net + "</span>)";
        return createCard(title, valueText, accentColor, false);
    }

    private JPanel createCard(String title, String valueText,
                              Color accentColor, boolean isAlert) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(isAlert
                ? new Color(192, 57, 43)
                : new Color(40, 44, 52));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        String titleStyle = isAlert ? "color:white;" : "color:#bdc3c7;";
        JLabel label = new JLabel(
                "<html><body style='color:white; "
                        + "font-family:Segoe UI; font-size:13px;'>"
                        + "<span style='" + titleStyle + "'>"
                        + title + ":</span> " + valueText
                        + "</body></html>"
        );
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private JPanel createStarvationCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(180, 20, 20));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0,
                        new Color(255, 50, 50)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        JLabel label = new JLabel(
                "<html><body style='color:white; "
                        + "font-family:Segoe UI; font-size:13px;'>"
                        + "<b>⚠️ STARVATION!</b>"
                        + "<span style='color:#ffaaaa; font-size:11px;'>"
                        + " Queue frozen | -1 AP/unit</span>"
                        + "</body></html>"
        );
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private void showStarvationAlert() {
        JDialog alert = new JDialog(
                (JFrame) SwingUtilities.getWindowAncestor(this), false);
        alert.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(180, 20, 20));
        panel.setBorder(BorderFactory.createLineBorder(
                new Color(255, 80, 80), 2));

        JLabel msg = new JLabel(
                "<html><center>"
                        + "<b style='color:white; font-size:16px;'>"
                        + "⚠️ STARVATION CRISIS!</b><br/>"
                        + "<span style='color:#ffcccc; font-size:12px;'>"
                        + "Your people are starving!<br/>"
                        + "Production queue frozen. Units lose 1 AP per turn."
                        + "</span></center></html>",
                SwingConstants.CENTER
        );
        msg.setBorder(BorderFactory.createEmptyBorder(15, 25, 15, 25));
        panel.add(msg, BorderLayout.CENTER);

        alert.setContentPane(panel);
        alert.pack();
        alert.setLocationRelativeTo(this);
        alert.setVisible(true);

        Timer closeTimer = new Timer(3000, e -> alert.dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private void showProductionNotification(String itemName) {
        JDialog notif = new JDialog(
                (JFrame) SwingUtilities.getWindowAncestor(this), false);
        notif.setUndecorated(true);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(39, 174, 96));
        panel.setBorder(BorderFactory.createLineBorder(
                new Color(46, 204, 113), 2));

        JLabel msg = new JLabel(
                "<html><center>"
                        + "<b style='color:white; font-size:14px;'>"
                        + "✅ Production Complete!</b><br/>"
                        + "<span style='color:#d5f5e3; font-size:12px;'>"
                        + itemName + " is ready."
                        + "</span></center></html>",
                SwingConstants.CENTER
        );
        msg.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        panel.add(msg, BorderLayout.CENTER);

        notif.setContentPane(panel);
        notif.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(
                screen.width  - notif.getWidth()  - 20,
                screen.height - notif.getHeight() - 60);
        notif.setVisible(true);

        Timer closeTimer = new Timer(2500, e -> notif.dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private void handleEndTurn() {
        if (gamePanel.isAnimating()) return;

        if (!confirmIdleMode
                && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("⚠️ IDLE UNITS! CONFIRM");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
    }
}