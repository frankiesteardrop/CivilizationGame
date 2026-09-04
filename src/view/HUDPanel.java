package view;

import com.google.gson.Gson;
import controller.MainController;
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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top HUD bar: resources, season/turn, unit counts, starvation alert,
 * active-turn indicator (B24), diplomacy badges (B23), in-game chat toggle (B25),
 * End Turn and Pause buttons.
 */
public class HUDPanel extends JPanel
        implements ResourceListener, UnitListener, ProductionListener,
        TurnListener, BuildingListener, MapListener, NotificationListener {

    private final MainController mainController;
    private final GamePanel      gamePanel;
    private final JPanel         infoContainer;
    private final JButton        endTurnBtn;
    private final JButton        pauseBtn;

    // Resource + status cards
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

    // B24 — active-turn indicator
    private final JLabel  activeTurnLabel;
    private boolean isMyTurn = false;

    // B23 — diplomacy badges panel
    private final JPanel diplomacyPanel;

    // B25 — in-game chat drawer
    //  ↓  'final' REMOVED — these are assigned inside buildChatDrawer(), not constructor directly
    private final JPanel    chatDrawer;   // chatDrawer itself IS assigned in constructor → fine
    private       JTextArea  chatArea;    // assigned inside buildChatDrawer() → cannot be final
    private       JTextField chatInput;   // assigned inside buildChatDrawer() → cannot be final
    private boolean chatVisible = false;

    private final Gson gson = new Gson();
    private boolean confirmIdleMode      = false;
    private boolean isStarving           = false;
    private boolean starvationAlertShown = false;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel      = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(25, 28, 33));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 4, 0, new Color(41, 128, 185)),
                new EmptyBorder(6, 12, 6, 12)));

        // ── Resource cards ────────────────────────────────────────────────────
        infoContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        infoContainer.setOpaque(false);

        foodCard      = new HUDCard("🍔 Food",      new Color(46, 204, 113),  false);
        woodCard      = new HUDCard("🪵 Wood",      new Color(211, 84, 0),    false);
        stoneCard     = new HUDCard("🪨 Stone",     new Color(149, 165, 166), false);
        ironCard      = new HUDCard("⚙️ Iron",      new Color(243, 156, 18),  false);
        queueCard     = new HUDCard("🏗️ Queue",     new Color(241, 196, 15),  false);
        popCard       = new HUDCard("👥 Units",     new Color(52, 152, 219),  false);
        turnCard      = new HUDCard("⏳ Turn",      new Color(155, 89, 182),  false);
        happinessCard = new HUDCard("😊 Happiness", new Color(255, 165, 0),   false);
        seasonCard    = new HUDCard("🌍 Season",    new Color(100, 180, 255), false);
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

        // ── B24: Active turn indicator ─────────────────────────────────────────
        activeTurnLabel = new JLabel("🎮 Single Player");
        activeTurnLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        activeTurnLabel.setForeground(new Color(189, 195, 199));
        activeTurnLabel.setOpaque(true);
        activeTurnLabel.setBackground(new Color(35, 40, 52));
        activeTurnLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(44, 62, 80), 1),
                new EmptyBorder(3, 8, 3, 8)));

        // ── B23: Diplomacy badges panel ────────────────────────────────────────
        diplomacyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        diplomacyPanel.setOpaque(false);
        diplomacyPanel.setVisible(false);

        // Left: turn indicator + resources + diplomacy
        JPanel leftRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        leftRow.setOpaque(false);
        leftRow.add(activeTurnLabel);
        leftRow.add(infoContainer);
        leftRow.add(diplomacyPanel);
        add(leftRow, BorderLayout.CENTER);

        // ── Buttons ───────────────────────────────────────────────────────────
        pauseBtn   = buildPauseButton();
        endTurnBtn = buildEndTurnButton();
        JButton chatBtn = buildChatToggleButton();

        JPanel eastPanel = new JPanel(new GridLayout(1, 3, 6, 0));
        eastPanel.setOpaque(false);
        eastPanel.add(chatBtn);
        eastPanel.add(pauseBtn);
        eastPanel.add(endTurnBtn);
        add(eastPanel, BorderLayout.EAST);

        // ── B25: Build chat drawer ─────────────────────────────────────────────
        // chatDrawer IS assigned here directly → can stay final
        // chatArea and chatInput are assigned inside buildChatDrawer() → must NOT be final
        chatDrawer = buildChatDrawer();
        chatDrawer.setVisible(false);

        // ── Listeners + periodic sync ─────────────────────────────────────────
        GameEventDispatcher.addListener(this);
        new Timer(500, e -> updateHUD()).start();
        updateHUD();
    }

    // ─── B24: Active Turn Indicator ───────────────────────────────────────────

    /**
     * Updates the turn indicator label.
     * Called by {@link network.client.ClientMessageDispatcher} on GAME_STATE_UPDATE.
     *
     * @param activePlayerName display name/id of the active player (null = single-player)
     * @param isMyTurn         true when it is this client's turn
     */
    public void setActiveTurnInfo(String activePlayerName, boolean isMyTurn) {
        this.isMyTurn = isMyTurn;
        SwingUtilities.invokeLater(() -> {
            if (activePlayerName == null) {
                activeTurnLabel.setText("🎮 Single Player");
                activeTurnLabel.setBackground(new Color(35, 40, 52));
                activeTurnLabel.setForeground(new Color(189, 195, 199));
            } else if (isMyTurn) {
                activeTurnLabel.setText("✅ YOUR TURN");
                activeTurnLabel.setBackground(new Color(39, 120, 60));
                activeTurnLabel.setForeground(Color.WHITE);
            } else {
                activeTurnLabel.setText("⏳ " + activePlayerName + "'s Turn");
                activeTurnLabel.setBackground(new Color(60, 40, 20));
                activeTurnLabel.setForeground(new Color(230, 180, 80));
            }
        });
    }

    // ─── B23: Diplomacy Panel ─────────────────────────────────────────────────

    /**
     * Rebuilds diplomacy badges for all known opponents.
     * Called by {@link network.client.ClientMessageDispatcher} on DIPLOMACY_EVENT.
     *
     * @param playerStatuses map: playerId → [displayName, status]
     *                       status ∈ {"Enemy", "Allied", "Neutral"}
     */
    public void updateDiplomacyStatus(Map<String, String[]> playerStatuses) {
        SwingUtilities.invokeLater(() -> {
            diplomacyPanel.removeAll();
            if (playerStatuses == null || playerStatuses.isEmpty()) {
                diplomacyPanel.setVisible(false);
                return;
            }

            JLabel header = new JLabel("Diplomacy: ");
            header.setFont(new Font("Segoe UI", Font.BOLD, 11));
            header.setForeground(new Color(150, 160, 180));
            diplomacyPanel.add(header);

            for (Map.Entry<String, String[]> entry : playerStatuses.entrySet()) {
                String name   = entry.getValue()[0];
                String status = entry.getValue()[1];

                Color  bg;
                String icon;
                switch (status) {
                    case "Enemy"  -> { bg = new Color(140, 30, 30); icon = "⚔️"; }
                    case "Allied" -> { bg = new Color(30, 100, 30); icon = "🤝"; }
                    default       -> { bg = new Color(50, 55, 70);  icon = "🔘"; }
                }

                JLabel badge = new JLabel(icon + " " + name);
                badge.setFont(new Font("Segoe UI", Font.BOLD, 11));
                badge.setForeground(Color.WHITE);
                badge.setOpaque(true);
                badge.setBackground(bg);
                badge.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(bg.brighter(), 1),
                        new EmptyBorder(2, 6, 2, 6)));
                badge.setToolTipText(name + ": " + status);
                diplomacyPanel.add(badge);
            }
            diplomacyPanel.setVisible(true);
            diplomacyPanel.revalidate();
            diplomacyPanel.repaint();
        });
    }

    // ─── B25: In-Game Chat ────────────────────────────────────────────────────

    /** Appends a formatted message to the in-game chat area (thread-safe). */
    public void appendGameChatMessage(String formattedMessage) {
        SwingUtilities.invokeLater(() -> {
            chatArea.append(formattedMessage);
            chatArea.setCaretPosition(chatArea.getDocument().getLength());
        });
    }

    // ─── onOurTurnStarted (B8) ────────────────────────────────────────────────

    /** Re-enables the End Turn button and updates the turn indicator. */
    public void onOurTurnStarted() {
        SwingUtilities.invokeLater(() -> {
            endTurnBtn.setEnabled(true);
            endTurnBtn.setText("END TURN");
            endTurnBtn.setBackground(new Color(192, 57, 43));
            confirmIdleMode = false;
            setActiveTurnInfo(
                    mainController.getNetworkManager() != null ? "You" : null, true);
        });
    }

    // ─── HUD Update ───────────────────────────────────────────────────────────

    private void updateHUD() {
        if (confirmIdleMode && !mainController.getTurnController().hasIdleUnits()) {
            resetEndTurnButton();
        }

        GameMap   map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();

        int netFood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.FOOD);
        int netWood  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.WOOD);
        int netStone = mainController.getEconomyController().calculateNetProduction(map, ResourceType.STONE);
        int netIron  = mainController.getEconomyController().calculateNetProduction(map, ResourceType.IRON);

        foodCard .updateValue(fmt(inv, ResourceType.FOOD,  netFood));
        woodCard .updateValue(fmt(inv, ResourceType.WOOD,  netWood));
        stoneCard.updateValue(fmt(inv, ResourceType.STONE, netStone));
        ironCard .updateValue(fmt(inv, ResourceType.IRON,  netIron));

        ProductionCommand task = map.getTownHall().getProductionQueue().peek();
        if (task != null) {
            String frozen = (isStarving && task.isPopulationTask())
                    ? " <span style='color:#e74c3c;'>❄️ FROZEN</span>" : "";
            queueCard.updateValue(task.getName() + " (" + task.getTurnsRemaining() + "T)" + frozen);
        } else {
            queueCard.updateValue("<span style='color:#7f8c8d;'>Idle</span>");
        }

        long milCount   = map.getMilitaryUnitCount();
        int  milCap     = map.getMilitaryUnitCap();
        long expCount   = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Explorer).count();
        long buildCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Builder).count();
        long workCount  = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof Worker).count();
        long expndCount = map.getUnits().stream().filter(u -> u.isAlive() && u instanceof BorderExpander).count();
        String milColor = (milCount >= milCap) ? "#e74c3c" : "#2ecc71";
        popCard.updateValue(
                "<span style='color:" + milColor + ";'>⚔️ " + milCount + "/" + milCap + "</span>"
                        + " | 👥 " + map.getAliveUnitsCount()
                        + " <span style='font-size:10px; color:#bdc3c7;'>"
                        + "(E:" + expCount + " B:" + buildCount
                        + " W:" + workCount + " X:" + expndCount + ")"
                        + "</span>");

        happinessCard.updateValue(formatHappiness(mainController.getEconomyController().getEffectiveHappiness(map)));
        seasonCard   .updateValue(formatSeason(map.getCurrentSeason()));
        seasonCard.setToolTipText(seasonTooltip(map.getCurrentSeason()));

        int turn = map.getCurrentTurn();
        turnCard.updateValue(turn + " <span style='color:#7f8c8d; font-size:10px;'>(" + ((turn - 1) % 10 + 1) + "/10)</span>");
        starvationAlertCard.setVisible(isStarving);
    }

    // ─── Button Builders ──────────────────────────────────────────────────────

    private JButton buildPauseButton() {
        JButton btn = new JButton("⏸  PAUSE  [ESC]");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(new Color(45, 52, 70));
        btn.setForeground(new Color(175, 185, 210));
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 14, 8, 14));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(62, 72, 98)); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(new Color(45, 52, 70)); }
        });
        btn.addActionListener(e -> gamePanel.openPauseMenu());
        return btn;
    }

    private JButton buildEndTurnButton() {
        JButton btn = new JButton("END TURN");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(new Color(192, 57, 43));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 20, 8, 20));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(btn.getBackground().brighter()); }
            @Override public void mouseExited (MouseEvent e) { updateButtonColor(); }
        });
        btn.addActionListener(e -> handleEndTurn());
        return btn;
    }

    private JButton buildChatToggleButton() {
        JButton btn = new JButton("💬 Chat");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(new Color(35, 70, 110));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 12, 8, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setOpaque(true);
        btn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(52, 100, 150)); }
            @Override public void mouseExited (MouseEvent e) { btn.setBackground(new Color(35, 70, 110)); }
        });
        btn.addActionListener(e -> toggleChat());
        return btn;
    }

    // ─── B25: Chat Drawer Builder ──────────────────────────────────────────────

    /**
     * Builds the sliding chat drawer panel.
     *
     * <p>IMPORTANT: {@code chatArea} and {@code chatInput} are instance fields
     * that are assigned here. They MUST NOT be declared {@code final} because
     * Java requires final instance fields to be assigned directly in the
     * constructor, not inside a method called by it.
     */
    private JPanel buildChatDrawer() {
        JPanel drawer = new JPanel(new BorderLayout(4, 4));
        drawer.setBackground(new Color(18, 22, 30));
        drawer.setPreferredSize(new Dimension(0, 180));
        drawer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(2, 0, 0, 0, new Color(52, 130, 215)),
                new EmptyBorder(6, 10, 6, 10)));

        // chatArea assigned here — field must NOT be final
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

        // chatInput assigned here — field must NOT be final
        chatInput = new JTextField();
        chatInput.setBackground(new Color(28, 33, 48));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        chatInput.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 130, 215), 1),
                new EmptyBorder(4, 8, 4, 8)));
        chatInput.setToolTipText("Type a message and press Enter to send");
        chatInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
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
        if (parent instanceof JPanel wrapper
                && wrapper.getLayout() instanceof BorderLayout) {
            if (chatVisible) {
                wrapper.add(chatDrawer, BorderLayout.SOUTH);
            } else {
                wrapper.remove(chatDrawer);
            }
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

    // ─── End Turn ─────────────────────────────────────────────────────────────

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

        // Single-player mode: idle-unit confirmation guard
        if (!confirmIdleMode && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("⚠️ IDLE UNITS! CONFIRM");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
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

    // ─── Formatting Helpers ───────────────────────────────────────────────────

    private String fmt(Inventory inv, ResourceType type, int net) {
        int amount = inv.getResourceAmount(type);
        int cap    = inv.getCapacity(type);
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71";
        String sign     = net > 0 ? "+" : "";
        return amount + "<span style='color:#7f8c8d'>/" + cap + "</span> "
                + "(<span style='color:" + netColor + "'>" + sign + net + "</span>)";
    }

    private String formatHappiness(int h) {
        String sign = h > 0 ? "+" : "";
        String label; String color;
        if      (h >= 3)  { label = "✨ Golden Age"; color = "#f1c40f"; }
        else if (h >= -2) { label = "😊 Normal";     color = "#2ecc71"; }
        else if (h >= -4) { label = "😠 Discontent"; color = "#e67e22"; }
        else              { label = "🔥 Rebellion";  color = "#e74c3c"; }
        return sign + h + " <span style='color:" + color
                + "; font-size:11px;'>[" + label + "]</span>";
    }

    private String formatSeason(Season s) {
        return switch (s) {
            case SPRING -> "<span style='color:#a8e063;'>🌸 Spring</span>";
            case SUMMER -> "<span style='color:#f9d423;'>☀️ Summer</span>";
            case AUTUMN -> "<span style='color:#e67e22;'>🍂 Autumn</span>";
            case WINTER -> "<span style='color:#a8d8ea;'>❄️ Winter</span>";
        };
    }

    private String seasonTooltip(Season s) {
        return switch (s) {
            case SPRING -> "<html><b style='color:#a8e063;'>🌸 Spring</b><br/>Farms & Stables +1 Food/turn</html>";
            case SUMMER -> "<html><b style='color:#f9d423;'>☀️ Summer</b><br/>No seasonal effects</html>";
            case AUTUMN -> "<html><b style='color:#e67e22;'>🍂 Autumn</b><br/>Water +1 AP | Flood risk</html>";
            case WINTER -> "<html><b style='color:#a8d8ea;'>❄️ Winter</b><br/>Farms −1 Food | Land +1 AP</html>";
        };
    }

    // ─── Notification / Starvation UI ─────────────────────────────────────────

    private JPanel createStarvationCard() {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(180, 20, 20));
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, new Color(255, 50, 50)),
                new EmptyBorder(6, 12, 6, 12)));
        JLabel label = new JLabel(
                "<html><body style='color:white;'>"
                        + "<b>⚠️ STARVATION!</b>"
                        + "<span style='color:#ffaaaa; font-size:11px;'> Population frozen | -1 AP/unit</span>"
                        + "</body></html>");
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
                "<html><center><b style='color:white; font-size:16px;'>⚠️ STARVATION!</b><br/>"
                        + "<span style='color:#ffcccc;'>Population frozen. Units lose 1 AP/turn.</span></center></html>",
                SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(15, 25, 15, 25));
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
                "<html><center><b style='color:white;'>✅ Production Complete!</b><br/>"
                        + "<span style='color:#d5f5e3;'>" + itemName + " is ready.</span></center></html>",
                SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(12, 20, 12, 20));
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
        JLabel msg = new JLabel(
                "<html><center><span style='color:white;'>" + message + "</span></center></html>",
                SwingConstants.CENTER);
        msg.setBorder(new EmptyBorder(10, 18, 10, 18));
        p.add(msg);
        notif.setContentPane(p);
        notif.pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        notif.setLocation(20, screen.height - notif.getHeight() - 60);
        notif.setVisible(true);
        new Timer(3500, e -> notif.dispose()) {{ setRepeats(false); start(); }};
    }

    // ─── HUDCard ──────────────────────────────────────────────────────────────

    private static class HUDCard extends JPanel {
        private final JLabel label;
        private final String title;
        private final String titleStyle;

        HUDCard(String title, Color accent, boolean isAlert) {
            this.title      = title;
            this.titleStyle = isAlert ? "color:white;" : "color:#bdc3c7;";
            setLayout(new BorderLayout());
            setBackground(isAlert ? new Color(192, 57, 43) : new Color(40, 44, 52));
            setOpaque(true);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, accent),
                    new EmptyBorder(6, 12, 6, 12)));
            label = new JLabel();
            add(label, BorderLayout.CENTER);
        }

        void updateValue(String valueText) {
            label.setText("<html><body style='font-family:Segoe UI; font-size:13px;'>"
                    + "<span style='" + titleStyle + "'>" + title + ":</span> "
                    + valueText + "</body></html>");
        }
    }

    // ─── Event Listener Implementations ──────────────────────────────────────

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