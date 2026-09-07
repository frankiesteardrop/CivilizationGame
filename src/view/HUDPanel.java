package view;

import com.google.gson.Gson;
import controller.MainController;
import controller.TradeController;
import model.*;
import network.client.NetworkManager;
import network.messages.game.EndTurnRequest;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HUDPanel extends JPanel
        implements ResourceListener, UnitListener, ProductionListener,
        TurnListener, BuildingListener, MapListener, NotificationListener {

    private final MainController mainController;
    private final GamePanel      gamePanel;
    private final JPanel         infoContainer;
    private final JButton        endTurnBtn;
    private final JButton        pauseBtn;
    private final JButton        inboxBtn;

    private final HUDCard foodCard;
    private final HUDCard woodCard;
    private final HUDCard stoneCard;
    private final HUDCard ironCard;
    private final HUDCard queueCard;
    private final HUDCard popCard;
    private final HUDCard turnCard;
    private final HUDCard happinessCard;
    private final HUDCard seasonCard;
    private final JPanel  starvationAlertCard;

    private final JLabel  activeTurnLabel;
    private boolean isMyTurn = false;
    private final JPanel diplomacyPanel;

    private final JPanel    chatDrawer;
    private       JTextArea  chatArea;
    private       JTextField chatInput;
    private boolean chatVisible = false;

    private final Gson gson = new Gson();
    private boolean confirmIdleMode      = false;
    private boolean isStarving           = false;
    private boolean starvationAlertShown = false;

    private List<TradeOffer> pendingOffers = new ArrayList<>();

    private final Timer updateTimer;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel      = gamePanel;

        setLayout(new BorderLayout(4, 0));
        setBackground(new Color(25, 28, 33));
        setPreferredSize(new Dimension(0, 48));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(41, 128, 185)),
                new EmptyBorder(4, 6, 4, 6)));

        JPanel westPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        westPanel.setOpaque(false);

        activeTurnLabel = new JLabel("🎮 Single Player");
        activeTurnLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        activeTurnLabel.setForeground(new Color(189, 195, 199));
        activeTurnLabel.setOpaque(true);
        activeTurnLabel.setBackground(new Color(35, 40, 52));
        activeTurnLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(44, 62, 80), 1),
                new EmptyBorder(3, 6, 3, 6)));

        diplomacyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        diplomacyPanel.setOpaque(false);
        diplomacyPanel.setVisible(false);

        westPanel.add(activeTurnLabel);
        westPanel.add(diplomacyPanel);
        add(westPanel, BorderLayout.WEST);

        infoContainer = new JPanel(new FlowLayout(FlowLayout.CENTER, 2, 0));
        infoContainer.setOpaque(false);

        foodCard      = new HUDCard("🍔", new Color(46, 204, 113), false);
        woodCard      = new HUDCard("🪵", new Color(211, 84, 0),   false);
        stoneCard     = new HUDCard("🪨", new Color(149, 165, 166),false);
        ironCard      = new HUDCard("⚙️", new Color(243, 156, 18), false);
        queueCard     = new HUDCard("🏗️", new Color(241, 196, 15), false);
        popCard       = new HUDCard("👥", new Color(52, 152, 219), false);
        happinessCard = new HUDCard("😊", new Color(255, 165, 0),  false);
        seasonCard    = new HUDCard("🌍", new Color(100, 180, 255),false);
        turnCard      = new HUDCard("⏳", new Color(155, 89, 182), false);

        starvationAlertCard = createStarvationCard();
        starvationAlertCard.setVisible(false);

        infoContainer.add(foodCard);
        infoContainer.add(woodCard);
        infoContainer.add(stoneCard);
        infoContainer.add(ironCard);
        infoContainer.add(queueCard);
        infoContainer.add(popCard);
        infoContainer.add(happinessCard);
        infoContainer.add(seasonCard);
        infoContainer.add(turnCard);
        infoContainer.add(starvationAlertCard);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(infoContainer, BorderLayout.CENTER);
        add(centerWrapper, BorderLayout.CENTER);
        JPanel eastPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
        eastPanel.setOpaque(false);

        inboxBtn   = buildActionButton("📥 Inbox", new Color(40, 44, 52), Color.WHITE);
        JButton chatBtn = buildActionButton("💬 Chat", new Color(35, 70, 110), Color.WHITE);
        pauseBtn   = buildActionButton("⏸ PAUSE", new Color(45, 52, 70), new Color(175, 185, 210));
        endTurnBtn = buildActionButton("END TURN", new Color(192, 57, 43), Color.WHITE);

        inboxBtn.addActionListener(e -> {
            if (mainController.getNetworkManager() == null) {
                GameEventDispatcher.fireNotification("⚠️ Trade Inbox is only available in Multiplayer.");
                return;
            }
            JFrame parentFrame = (JFrame) SwingUtilities.getWindowAncestor(this);
            TradeController tradeController = new TradeController(mainController.getNetworkManager());
            new TradeInboxDialog(parentFrame, tradeController, pendingOffers).setVisible(true);
        });

        chatBtn.addActionListener(e -> toggleChat());
        pauseBtn.addActionListener(e -> gamePanel.openPauseMenu());
        endTurnBtn.addActionListener(e -> handleEndTurn());

        eastPanel.add(inboxBtn);
        eastPanel.add(chatBtn);
        eastPanel.add(pauseBtn);
        eastPanel.add(endTurnBtn);
        add(eastPanel, BorderLayout.EAST);

        // ─── Chat Drawer
        chatDrawer = buildChatDrawer();
        chatDrawer.setVisible(false);

        GameEventDispatcher.addListener(this);

        updateTimer = new Timer(500, e -> updateHUD());
        updateTimer.start();

        updateHUD();
    }

    public void cleanup() {
        if (updateTimer != null && updateTimer.isRunning()) {
            updateTimer.stop();
        }
        GameEventDispatcher.removeListener(this);
    }

    private void updateHUD() {
        if (confirmIdleMode && !mainController.getTurnController().hasIdleUnits()) {
            resetEndTurnButton();
        }

        GameMap map = mainController.getGameMap();
        TownHall playerTH = map.getPlayerTownHall(mainController.getMyPlayerId());
        if (playerTH == null) {
            foodCard.updateValue("0");
            woodCard.updateValue("0");
            stoneCard.updateValue("0");
            ironCard.updateValue("0");
            queueCard.updateValue("<span style='color:#e74c3c;'>Destroyed</span>");
            return;
        }

        Inventory inv = playerTH.getInventory();

        int netFood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.FOOD);
        int netWood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.WOOD);
        int netStone = mainController.getEconomyController().calculateNetProduction(map, ResourceType.STONE);
        int netIron  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.IRON);

        foodCard.updateValue(fmtConcise(inv, ResourceType.FOOD, netFood));
        foodCard.setToolTipText(fmtTooltip(inv, ResourceType.FOOD, netFood));

        woodCard.updateValue(fmtConcise(inv, ResourceType.WOOD, netWood));
        woodCard.setToolTipText(fmtTooltip(inv, ResourceType.WOOD, netWood));

        stoneCard.updateValue(fmtConcise(inv, ResourceType.STONE, netStone));
        stoneCard.setToolTipText(fmtTooltip(inv, ResourceType.STONE, netStone));

        ironCard.updateValue(fmtConcise(inv, ResourceType.IRON, netIron));
        ironCard.setToolTipText(fmtTooltip(inv, ResourceType.IRON, netIron));

        ProductionCommand task = playerTH.getProductionQueue().peek();
        if (task != null) {
            String frozen = (isStarving && task.isPopulationTask()) ? " <span style='color:#e74c3c;'>❄️</span>" : "";
            queueCard.updateValue(task.getTurnsRemaining() + "T" + frozen);
            queueCard.setToolTipText("Producing: " + task.getName() + " (" + task.getTurnsRemaining() + " turns left)");
        } else {
            queueCard.updateValue("<span style='color:#7f8c8d;'>Idle</span>");
            queueCard.setToolTipText("Queue is empty");
        }

        long milCount   = map.getMilitaryUnitCount();
        int  milCap     = map.getMilitaryUnitCap();
        long expCount   = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Explorer).count();
        long buildCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Builder).count();
        long workCount  = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Worker).count();
        long expndCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof BorderExpander).count();
        String milColor = (milCount >= milCap) ? "#e74c3c" : "#2ecc71";

        popCard.updateValue("<span style='color:" + milColor + ";'>" + milCount + "</span>/" + milCap + " | " + map.getAliveUnitsCount());
        popCard.setToolTipText(String.format("<html>Military: %d / %d<br/>Civilians: %d<br/>(Exp:%d, Bld:%d, Wrk:%d, Expnd:%d)</html>",
                milCount, milCap, map.getAliveUnitsCount(), expCount, buildCount, workCount, expndCount));

        int happiness = mainController.getEconomyController().getEffectiveHappiness(map);
        happinessCard.updateValue(happiness > 0 ? "+" + happiness : String.valueOf(happiness));
        happinessCard.setToolTipText("Empire Happiness: " + happiness);

        Season currentSeason = map.getCurrentSeason();
        seasonCard.updateValue(currentSeason.name().substring(0, 1) + currentSeason.name().substring(1).toLowerCase());
        seasonCard.setToolTipText(seasonTooltip(currentSeason));

        int turn = map.getCurrentTurn();
        turnCard.updateValue(String.valueOf(turn));
        turnCard.setToolTipText("Global Turn: " + turn + " | Season Turn: " + ((turn - 1) % 10 + 1) + "/10");

        starvationAlertCard.setVisible(isStarving);
    }

    private String fmtConcise(Inventory inv, ResourceType type, int net) {
        int amount = inv.getResourceAmount(type);
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71";
        String sign     = net > 0 ? "+" : "";
        return amount + " <span style='color:" + netColor + "; font-size:9px;'>(" + sign + net + ")</span>";
    }

    private String fmtTooltip(Inventory inv, ResourceType type, int net) {
        return String.format("%s: %d / %d | Net Production: %+d",
                type.name(), inv.getResourceAmount(type), inv.getCapacity(type), net);
    }

    private JButton buildActionButton(String text, Color bg, Color fg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (btn.isEnabled()) btn.setBackground(btn.getBackground().brighter());
            }
            @Override public void mouseExited (MouseEvent e) {
                if (btn == endTurnBtn) updateButtonColor();
                else if (btn == inboxBtn && !pendingOffers.isEmpty()) btn.setBackground(new Color(230, 126, 34));
                else btn.setBackground(bg);
            }
        });
        return btn;
    }

    private void updateButtonColor() {
        endTurnBtn.setBackground(confirmIdleMode ? new Color(230, 126, 34) : new Color(192, 57, 43));
    }

    private void resetEndTurnButton() {
        if (confirmIdleMode) {
            confirmIdleMode = false;
            endTurnBtn.setText("END TURN");
            updateButtonColor();
        }
    }

    private void handleEndTurn() {
        if (gamePanel.isAnimating() || mainController.isProcessingTurn()) return;

        NetworkManager nm = mainController.getNetworkManager();
        if (nm != null) {
            nm.sendRequest(gson.toJson(new EndTurnRequest()));
            endTurnBtn.setEnabled(false);
            endTurnBtn.setText("⏳ Waiting...");
            confirmIdleMode = false;
            setActiveTurnInfo("Waiting...", false);
            return;
        }

        if (!confirmIdleMode && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("⚠️ IDLE UNITS!");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
    }

    public void onOurTurnStarted() {
        SwingUtilities.invokeLater(() -> {
            endTurnBtn.setEnabled(true);
            endTurnBtn.setText("END TURN");
            endTurnBtn.setBackground(new Color(192, 57, 43));
            confirmIdleMode = false;
            setActiveTurnInfo(mainController.getNetworkManager() != null ? "You" : null, true);
        });
    }

    public void setActiveTurnInfo(String activePlayerName, boolean isMyTurn) {
        this.isMyTurn = isMyTurn;
        mainController.setMyTurn(isMyTurn);
        SwingUtilities.invokeLater(() -> {
            if (activePlayerName == null) {
                activeTurnLabel.setText("🎮 Single Player");
                activeTurnLabel.setBackground(new Color(35, 40, 52));
            } else if (isMyTurn) {
                activeTurnLabel.setText("✅ YOUR TURN");
                activeTurnLabel.setBackground(new Color(39, 120, 60));
            } else {
                activeTurnLabel.setText("⏳ " + activePlayerName + "'s Turn");
                activeTurnLabel.setBackground(new Color(60, 40, 20));
            }
        });
    }

    public void updateDiplomacyStatus(Map<String, String[]> playerStatuses) {
        SwingUtilities.invokeLater(() -> {
            diplomacyPanel.removeAll();
            if (playerStatuses == null || playerStatuses.isEmpty()) {
                diplomacyPanel.setVisible(false);
                return;
            }
            for (Map.Entry<String, String[]> entry : playerStatuses.entrySet()) {
                String name   = entry.getValue()[0];
                String status = entry.getValue()[1];
                Color bg = switch (status) {
                    case "Enemy"  -> new Color(140, 30, 30);
                    case "Allied" -> new Color(30, 100, 30);
                    default       -> new Color(50, 55, 70);
                };
                String icon = switch (status) {
                    case "Enemy" -> "⚔️"; case "Allied" -> "🤝"; default -> "🔘";
                };
                JLabel badge = new JLabel(icon + " " + name);
                badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
                badge.setForeground(Color.WHITE);
                badge.setOpaque(true);
                badge.setBackground(bg);
                badge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(bg.brighter(), 1),
                        new EmptyBorder(2, 5, 2, 5)));
                badge.setToolTipText(name + ": " + status);
                diplomacyPanel.add(badge);
            }
            diplomacyPanel.setVisible(true);
            diplomacyPanel.revalidate();
            diplomacyPanel.repaint();
        });
    }

    public void updateTradeInbox(List<TradeOffer> offers) {
        this.pendingOffers = offers;
        SwingUtilities.invokeLater(() -> {
            if (offers.isEmpty()) {
                inboxBtn.setText("📥 Inbox");
                inboxBtn.setBackground(new Color(40, 44, 52));
            } else {
                inboxBtn.setText("📥 Inbox (" + offers.size() + ")");
                inboxBtn.setBackground(new Color(230, 126, 34));
            }
        });
    }

    private JPanel buildChatDrawer() {
        JPanel drawer = new JPanel(new BorderLayout(4, 4));
        drawer.setBackground(new Color(18, 22, 30));
        drawer.setPreferredSize(new Dimension(0, 180));
        drawer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(52, 130, 215)),
                new EmptyBorder(6, 10, 6, 10)));

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(12, 15, 22));
        chatArea.setForeground(Color.WHITE);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);

        JScrollPane scroll = new JScrollPane(chatArea);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(12, 15, 22));

        chatInput = new JTextField();
        chatInput.setBackground(new Color(28, 33, 48));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 130, 215), 1),
                new EmptyBorder(4, 8, 4, 8)));
        chatInput.addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) sendChatMessage();
            }
        });

        JLabel chatHeader = new JLabel("💬 Game Chat");
        chatHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        chatHeader.setForeground(new Color(52, 130, 215));
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(chatHeader, BorderLayout.WEST);

        drawer.add(top,       BorderLayout.NORTH);
        drawer.add(scroll,    BorderLayout.CENTER);
        drawer.add(chatInput, BorderLayout.SOUTH);
        return drawer;
    }

    private void toggleChat() {
        chatVisible = !chatVisible;
        Container parent = getParent();
        if (parent instanceof JPanel wrapper && wrapper.getLayout() instanceof BorderLayout) {
            if (chatVisible) wrapper.add(chatDrawer, BorderLayout.SOUTH);
            else wrapper.remove(chatDrawer);
            wrapper.revalidate();
            wrapper.repaint();
        }
        chatDrawer.setVisible(chatVisible);
        if (chatVisible) chatInput.requestFocusInWindow();
    }

    private void sendChatMessage() {
        String text = chatInput.getText().trim();
        if (text.isEmpty()) return;
        NetworkManager nm = mainController.getNetworkManager();
        if (nm != null) {
            nm.sendRequest(gson.toJson(new network.messages.lobby.ChatSendRequest(text)));
        } else {
            appendGameChatMessage("[local] You: " + text + "\n");
        }
        chatInput.setText("");
    }

    public void appendGameChatMessage(String formattedMessage) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(formattedMessage);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    private JPanel createStarvationCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(180, 20, 20));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(255, 50, 50)),
                new EmptyBorder(2, 6, 2, 6)));
        JLabel label = new JLabel("<html><body style='color:white; font-size:10px;'><b>⚠️ STARVING</b></body></html>");
        card.add(label, BorderLayout.CENTER);
        return card;
    }

    private void showStarvationAlert() {
        JDialog alert = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), false);
        alert.setUndecorated(true);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(180, 20, 20));
        p.setBorder(BorderFactory.createLineBorder(new Color(255, 80, 80), 2));
        JLabel msg = new JLabel(
                "<html><center><b style='color:white; font-size:14px;'>⚠️ STARVATION!</b><br/>"
                        + "<span style='color:#ffcccc; font-size:11px;'>Population frozen. Units lose 1 AP/turn.</span></center></html>",
                SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(10, 20, 10, 20));
        p.add(msg);
        alert.setContentPane(p);
        alert.pack();
        alert.setLocationRelativeTo(this);
        alert.setVisible(true);
        new Timer(3000, e -> alert.dispose()) {{ setRepeats(false); start(); }};
    }

    private void showProductionNotification(String itemName) {
        JDialog notif = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), false);
        notif.setUndecorated(true);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(39, 174, 96));
        p.setBorder(BorderFactory.createLineBorder(new Color(46, 204, 113), 2));
        JLabel msg = new JLabel(
                "<html><center><b style='color:white; font-size:12px;'>✅ Production Complete!</b><br/>"
                        + "<span style='color:#d5f5e3; font-size:11px;'>" + itemName + " is ready.</span></center></html>",
                SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(8, 16, 8, 16));
        p.add(msg);
        notif.setContentPane(p);
        notif.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(screen.width - notif.getWidth() - 20, screen.height - notif.getHeight() - 60);
        notif.setVisible(true);
        new Timer(2500, e -> notif.dispose()) {{ setRepeats(false); start(); }};
    }

    private void showDisasterNotification(String message) {
        JDialog notif = new JDialog((JFrame) SwingUtilities.getWindowAncestor(this), false);
        notif.setUndecorated(true);
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(new Color(120, 60, 0));
        p.setBorder(BorderFactory.createLineBorder(new Color(230, 120, 0), 2));
        JLabel msg = new JLabel("<html><center><span style='color:white; font-size:11px;'>" + message + "</span></center></html>", SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(8, 14, 8, 14));
        p.add(msg);
        notif.setContentPane(p);
        notif.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(20, screen.height - notif.getHeight() - 60);
        notif.setVisible(true);
        new Timer(3500, e -> notif.dispose()) {{ setRepeats(false); start(); }};
    }

    private String seasonTooltip(Season s) {
        return switch (s) {
            case SPRING -> "<html><b style='color:#a8e063;'>🌸 Spring</b><br/>Farms & Stables +1 Food/turn</html>";
            case SUMMER -> "<html><b style='color:#f9d423;'>☀️ Summer</b><br/>No seasonal effects</html>";
            case AUTUMN -> "<html><b style='color:#e67e22;'>🍂 Autumn</b><br/>Water +1 AP | Flood risk</html>";
            case WINTER -> "<html><b style='color:#a8d8ea;'>❄️ Winter</b><br/>Farms −1 Food | Land +1 AP</html>";
        };
    }

    private static class HUDCard extends JPanel {
        private final JLabel label;
        private final String iconPrefix;

        HUDCard(String iconPrefix, Color accent, boolean isAlert) {
            this.iconPrefix = iconPrefix;
            setLayout(new BorderLayout());
            setBackground(isAlert ? new Color(192, 57, 43) : new Color(40, 44, 52));
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 2, 0, 0, accent),
                    new EmptyBorder(2, 5, 2, 5)));
            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        void updateValue(String valueText) {
            label.setText("<html><body style='font-family:Segoe UI; font-size:11px; color:white;'>"
                    + "<b>" + iconPrefix + "</b> " + valueText + "</body></html>");
        }
    }

    @Override public void onResourceChanged(ResourceType t, int a)             { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitMoved(Unit u, int oq, int or_, int nq, int nr) { SwingUtilities.invokeLater(() -> { resetEndTurnButton(); updateHUD(); }); }
    @Override public void onUnitKilled(Unit u)                                  { SwingUtilities.invokeLater(() -> { resetEndTurnButton(); updateHUD(); }); }
    @Override public void onUnitStateChanged(Unit u)                            { SwingUtilities.invokeLater(() -> { resetEndTurnButton(); updateHUD(); gamePanel.repaint(); }); }
    @Override public void onProductionCompleted(String name)                    { SwingUtilities.invokeLater(() -> { updateHUD(); showProductionNotification(name); }); }
    @Override public void onTurnEnded(int t)                                    { SwingUtilities.invokeLater(() -> { resetEndTurnButton(); updateHUD(); gamePanel.repaint(); }); }
    @Override public void onBuildingConstructed(Hex h)                          { SwingUtilities.invokeLater(() -> { updateHUD(); gamePanel.repaint(); }); }
    @Override public void onBuildingDestroyed(Hex h)                            { SwingUtilities.invokeLater(() -> { updateHUD(); gamePanel.repaint(); }); }
    @Override public void onBorderExpanded(int q, int r)                        { SwingUtilities.invokeLater(() -> { resetEndTurnButton(); updateHUD(); gamePanel.repaint(); }); }
    @Override public void onNotification(String msg)                            { SwingUtilities.invokeLater(() -> showDisasterNotification(msg)); }

    @Override
    public void onStarvationChanged(boolean starving) {
        SwingUtilities.invokeLater(() -> {
            boolean was = this.isStarving;
            this.isStarving = starving;
            updateHUD();
            if (starving && !was && !starvationAlertShown) {
                starvationAlertShown = true;
                showStarvationAlert();
            }
            if (!starving) starvationAlertShown = false;
        });
    }
}