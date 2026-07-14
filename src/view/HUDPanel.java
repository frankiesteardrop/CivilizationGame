package view;

import controller.MainController;
import model.GameEventDispatcher;
import model.GameMap;
import model.Hex;
import model.Inventory;
import model.ResourceType;
import model.TownHall;
import model.Unit;
import model.Explorer;
import model.Builder;
import model.Worker;
import model.BorderExpander;
import model.GameEventListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HUDPanel extends JPanel implements GameEventListener {

    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JPanel infoContainer;
    private final JButton endTurnBtn;

    private final HUDCard foodCard;
    private final HUDCard woodCard;
    private final HUDCard stoneCard;
    private final HUDCard ironCard;
    private final HUDCard queueCard;
    private final HUDCard popCard;
    private final HUDCard turnCard;
    private final JPanel starvationAlertCard;

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

        foodCard = new HUDCard("🍔 Food", new Color(46, 204, 113), false);
        woodCard = new HUDCard("🪵 Wood", new Color(211, 84, 0), false);
        stoneCard = new HUDCard("🪨 Stone", new Color(149, 165, 166), false);
        ironCard = new HUDCard("⚙️ Iron", new Color(243, 156, 18), false);
        queueCard = new HUDCard("🏗️ Queue", new Color(241, 196, 15), false);
        popCard = new HUDCard("👥 Pop", new Color(52, 152, 219), false);
        turnCard = new HUDCard("⏳ Turn", new Color(155, 89, 182), false);
        starvationAlertCard = createStarvationCard();
        starvationAlertCard.setVisible(false);

        infoContainer.add(foodCard);
        infoContainer.add(woodCard);
        infoContainer.add(stoneCard);
        infoContainer.add(ironCard);
        infoContainer.add(queueCard);
        infoContainer.add(popCard);
        infoContainer.add(turnCard);
        infoContainer.add(starvationAlertCard);

        GameEventDispatcher.addListener(this);

        Timer uiSyncTimer = new Timer(500, e -> updateHUD());
        uiSyncTimer.start();

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

    @Override
    public void onBorderExpanded(int centerQ, int centerR) {
        SwingUtilities.invokeLater(() -> {
            resetEndTurnButton();
            updateHUD();
            gamePanel.repaint();
        });
    }

    private void updateHUD() {
        if (confirmIdleMode && !mainController.getTurnController().hasIdleUnits()) {
            resetEndTurnButton();
        }

        GameMap map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();

        int maxFood  = inv.getCapacity(ResourceType.FOOD);
        int maxWood  = inv.getCapacity(ResourceType.WOOD);
        int maxStone = inv.getCapacity(ResourceType.STONE);
        int maxIron  = inv.getCapacity(ResourceType.IRON);

        int netFood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.FOOD);
        int netWood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.WOOD);
        int netStone = mainController.getEconomyController().calculateNetProduction(map, ResourceType.STONE);
        int netIron  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.IRON);

        foodCard.updateValue(formatResourceText(inv.getResourceAmount(ResourceType.FOOD), maxFood, netFood));
        woodCard.updateValue(formatResourceText(inv.getResourceAmount(ResourceType.WOOD), maxWood, netWood));
        stoneCard.updateValue(formatResourceText(inv.getResourceAmount(ResourceType.STONE), maxStone, netStone));
        ironCard.updateValue(formatResourceText(inv.getResourceAmount(ResourceType.IRON), maxIron, netIron));

        TownHall.ProductionTask currentTask = map.getTownHall().getProductionQueue().peek();
        if (currentTask != null) {
            if (isStarving && map.getTownHall().isPopulationTask(currentTask.getName())) {
                queueCard.updateValue(currentTask.getName() + " (" + currentTask.getTurnsRemaining() + "T) <span style='color:#e74c3c;'>❄️ FROZEN</span>");
            } else {
                queueCard.updateValue(currentTask.getName() + " (" + currentTask.getTurnsRemaining() + "T)");
            }
        } else {
            queueCard.updateValue("<span style='color:#7f8c8d;'>Idle</span>");
        }

        long expCount   = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Explorer).count();
        long buildCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Builder).count();
        long workCount  = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Worker).count();
        long expndCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof BorderExpander).count();

        String unitText = map.getAliveUnitsCount() + "/" + map.getUnitCap()
                + " <span style='font-size:10px; color:#bdc3c7;'>"
                + "(E:" + expCount + " B:" + buildCount + " W:" + workCount + " X:" + expndCount + ")</span>";

        popCard.updateValue(unitText);
        turnCard.updateValue(String.valueOf(map.getCurrentTurn()));

        starvationAlertCard.setVisible(isStarving);
    }

    private String formatResourceText(int amount, int max, int net) {
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71";
        String sign     = net > 0 ? "+" : "";
        return amount + "<span style='color:#7f8c8d'>/" + max + "</span> "
                + "(<span style='color:" + netColor + "'>" + sign + net + "</span>)";
    }

    private JPanel createStarvationCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(180, 20, 20));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(255, 50, 50)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        JLabel label = new JLabel(
                "<html><body style='color:white; font-family:Segoe UI; font-size:13px;'>"
                        + "<b>⚠️ STARVATION!</b>"
                        + "<span style='color:#ffaaaa; font-size:11px;'>"
                        + " Queue frozen | -1 AP/unit</span>"
                        + "</body></html>"
        );
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private void showStarvationAlert() {
        JDialog alert = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), false);
        alert.setUndecorated(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(180, 20, 20));
        panel.setBorder(BorderFactory.createLineBorder(new Color(255, 80, 80), 2));
        JLabel msg = new JLabel(
                "<html><center><b style='color:white; font-size:16px;'>⚠️ STARVATION CRISIS!</b><br/>"
                        + "<span style='color:#ffcccc; font-size:12px;'>"
                        + "Your people are starving!<br/>Production queue frozen. Units lose 1 AP per turn."
                        + "</span></center></html>", SwingConstants.CENTER
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
        JDialog notif = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), false);
        notif.setUndecorated(true);
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(39, 174, 96));
        panel.setBorder(BorderFactory.createLineBorder(new Color(46, 204, 113), 2));
        JLabel msg = new JLabel(
                "<html><center><b style='color:white; font-size:14px;'>✅ Production Complete!</b><br/>"
                        + "<span style='color:#d5f5e3; font-size:12px;'>" + itemName + " is ready."
                        + "</span></center></html>", SwingConstants.CENTER
        );
        msg.setBorder(BorderFactory.createEmptyBorder(12, 20, 12, 20));
        panel.add(msg, BorderLayout.CENTER);
        notif.setContentPane(panel);
        notif.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(screen.width - notif.getWidth() - 20, screen.height - notif.getHeight() - 60);
        notif.setVisible(true);

        Timer closeTimer = new Timer(2500, e -> notif.dispose());
        closeTimer.setRepeats(false);
        closeTimer.start();
    }

    private void handleEndTurn() {
        if (gamePanel.isAnimating()) return;

        if (!confirmIdleMode && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("⚠️ IDLE UNITS! CONFIRM");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
    }

    private static class HUDCard extends JPanel {
        private final JLabel label;
        private final String title;
        private final String titleStyle;

        public HUDCard(String title, Color accentColor, boolean isAlert) {
            this.title = title;
            this.titleStyle = isAlert ? "color:white;" : "color:#bdc3c7;";
            setLayout(new BorderLayout());
            setBackground(isAlert ? new Color(192, 57, 43) : new Color(40, 44, 52));
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));

            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        public void updateValue(String valueText) {
            label.setText("<html><body style='color:white; font-family:Segoe UI; font-size:13px;'>"
                    + "<span style='" + titleStyle + "'>"
                    + title + ":</span> " + valueText
                    + "</body></html>");
        }
    }
}