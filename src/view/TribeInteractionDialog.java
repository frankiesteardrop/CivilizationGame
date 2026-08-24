package view;

import controller.MainController;
import controller.TribeController;
import model.*;
import model.mission.Mission;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class TribeInteractionDialog extends JDialog {

    private static final Color BG_DARK      = new Color(25, 28, 35);
    private static final Color BG_CARD      = new Color(35, 39, 48);
    private static final Color ACCENT_BLUE  = new Color(52, 152, 219);
    private static final Color ACCENT_GREEN = new Color(46, 204, 113);
    private static final Color ACCENT_RED   = new Color(231, 76, 60);
    private static final Color ACCENT_GOLD  = new Color(241, 196, 15);
    private static final Color ACCENT_PURP  = new Color(155, 89, 182);
    private static final Color ACCENT_TEAL  = new Color(26, 188, 156);
    private static final Color TEXT_MAIN    = new Color(236, 240, 241);
    private static final Color TEXT_DIM     = new Color(127, 140, 141);
    private static final Color BTN_DISABLED = new Color(60, 65, 75);

    private final TribeCamp         camp;
    private final Tribe             tribe;
    private final TribeController tribeController;
    private final MainController  mainController;
    private final Runnable        onClose;

    public TribeInteractionDialog(JFrame parent, TribeCamp camp,
                                  MainController mainController, Runnable onClose) {
        super(parent, "Tribe Interaction", true);
        this.camp            = camp;
        this.tribe           = camp.getTribe();
        this.tribeController = mainController.getTribeController();
        this.mainController  = mainController;
        this.onClose         = onClose;

        setUndecorated(true);
        setSize(480, 700);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 2));

        root.add(buildHeader(),           BorderLayout.NORTH);
        root.add(buildScrollableActions(), BorderLayout.CENTER);
        root.add(buildFooter(),           BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setBackground(BG_CARD);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        JLabel title = new JLabel(getTribeEmoji() + "  " + tribe.getType().getDisplayName() + " Tribe");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_MAIN);

        Color statusColor = getStatusColor(tribe.getState().getName());
        JLabel statusLbl = new JLabel(tribe.getDetailedStatus());
        statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLbl.setForeground(statusColor);

        JPanel relationBar = buildRelationBar();

        JPanel textPanel = new JPanel(new GridLayout(3, 1, 2, 4));
        textPanel.setOpaque(false);
        textPanel.add(title);
        textPanel.add(statusLbl);
        textPanel.add(relationBar);
        panel.add(textPanel, BorderLayout.CENTER);

        JButton closeBtn = buildIconButton("✕", ACCENT_RED);
        closeBtn.addActionListener(e -> dispose());
        JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closePanel.setOpaque(false);
        closePanel.add(closeBtn);
        panel.add(closePanel, BorderLayout.EAST);

        return panel;
    }

    private JPanel buildRelationBar() {
        JPanel bar = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth(), h = getHeight();
                g2.setColor(new Color(50, 55, 65));
                g2.fillRoundRect(0, h/3, w, h/3, 4, 4);
                int rel   = tribe.getRelationship();
                int fillW = (int)((rel + 100) / 200.0 * w);
                g2.setColor(getStatusColor(tribe.getState().getName()));
                g2.fillRoundRect(0, h/3, fillW, h/3, 4, 4);
                g2.setColor(TEXT_DIM);
                g2.drawLine(w/2, h/4, w/2, 3*h/4);
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(0, 18));
        return bar;
    }

    private JScrollPane buildScrollableActions() {
        JPanel actionsPanel = new JPanel();
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setBackground(BG_DARK);
        actionsPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        actionsPanel.add(buildActionButton(
                "🎁  Send Gift",
                "Send resources to improve relations",
                tribe.canReceiveGift(),
                tribe.canReceiveGift() ? null : "Cannot gift to an enemy tribe",
                ACCENT_GREEN,
                this::showGiftDialog));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        boolean canTrade = tribe.canTrade() && !camp.hasTraded() && tribe.getType() != TribeType.WARRIOR;
        String tradeDisabledReason = tribe.getType() == TribeType.WARRIOR
                ? "Warrior tribe does not engage in commerce"
                : (!tribe.canTrade() ? "Requires Friendly status (≥20 relation)" : "Already traded this turn");

        actionsPanel.add(buildActionButton(
                "💱  Trade Resources",
                "Exchange resources at " + getTribeTradeRateLabel(),
                canTrade,
                tradeDisabledReason,
                ACCENT_BLUE,
                this::showTradeDialog));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        actionsPanel.add(buildActionButton(
                "📜  Mission Board",
                "View or Accept tribe missions",
                true,
                null,
                ACCENT_GOLD,
                this::showMissionInfo));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        boolean canDeliver = tribe.getMission() != null
                && tribe.getMission().getState().canDeliver();
        actionsPanel.add(buildActionButton(
                "✅  Deliver Mission",
                "Deliver completed mission for rewards",
                canDeliver,
                "No ready mission to deliver",
                ACCENT_GREEN,
                () -> {
                    if (tribeController.deliverMission(camp)) {
                        JOptionPane.showMessageDialog(this,
                                "Mission Delivered! Reward Applied.",
                                "Mission Complete ✅", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this,
                                "Delivery failed. Resources missing?",
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }
                    rebuildAndRefresh();
                }));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        boolean canAllianceState = tribe.canFormAlliance();
        String  allianceReason   = getAllianceDisabledReason();
        actionsPanel.add(buildActionButton(
                "🤝  Request Alliance",
                "Form a permanent alliance for ongoing bonuses",
                canAllianceState && allianceReason == null,
                allianceReason != null ? allianceReason
                        : (canAllianceState ? null : "Requires Allied status (≥70 relation)"),
                ACCENT_PURP,
                this::tryFormAlliance));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        boolean canWar = tribe.getState().canDeclareWar();
        actionsPanel.add(buildActionButton(
                "⚔️  Declare War",
                "Start a war — causes happiness penalty!",
                canWar,
                "Already at war with this tribe",
                ACCENT_RED,
                this::confirmDeclareWar));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        actionsPanel.add(buildActionButton(
                "🕊️  Request Peace",
                "End war (costs 30 Food + 30 Wood + 30 Iron)",
                tribe.canRequestPeace(),
                "Only available when at war",
                new Color(100, 180, 255),
                this::tryRequestPeace));

        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));

        actionsPanel.add(buildActionButton(
                "⭐  View Tribe Rewards",
                "See what bonuses become available at Friendly and Allied status",
                true,
                null,
                ACCENT_TEAL,
                this::showRewardsDialog));

        JScrollPane scroll = new JScrollPane(actionsPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 10));
        panel.setBackground(BG_CARD);
        panel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(60, 65, 75)));

        JButton closeBtn = new JButton("Close");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        closeBtn.setBackground(new Color(60, 65, 75));
        closeBtn.setForeground(TEXT_MAIN);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(8, 20, 8, 20));
        closeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        closeBtn.addActionListener(e -> dispose());
        panel.add(closeBtn);
        return panel;
    }

    private void showGiftDialog() {
        JDialog giftDlg = createSubDialog("🎁 Send Gift to " + tribe.getType().getDisplayName());
        giftDlg.setSize(440, 320);

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        JLabel rateInfo = makeLabel(
                "Rate: 10 Food/Wood → +2 | 10 Stone → +3 | 5 Iron → +3",
                TEXT_DIM, Font.PLAIN, 11);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        content.add(rateInfo, gbc);

        gbc.gridy = 1; gbc.gridwidth = 1;
        content.add(makeLabel("Resource:", TEXT_DIM, Font.PLAIN, 12), gbc);

        ResourceType[] resTypes  = {ResourceType.FOOD, ResourceType.WOOD,
                ResourceType.STONE, ResourceType.IRON};
        String[]       resLabels = {"🍔 Food", "🪵 Wood", "🪨 Stone", "⚙️ Iron"};
        JComboBox<String> resBox = new JComboBox<>(resLabels);
        resBox.setBackground(BG_CARD);
        resBox.setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2;
        content.add(resBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        content.add(makeLabel("Amount:", TEXT_DIM, Font.PLAIN, 12), gbc);

        JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 9999, 5));
        amountSpinner.getEditor().getComponent(0).setBackground(BG_CARD);
        ((JSpinner.DefaultEditor) amountSpinner.getEditor()).getTextField().setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2;
        content.add(amountSpinner, gbc);

        JLabel preview = makeLabel("Relation gain: +2", ACCENT_GREEN, Font.BOLD, 13);
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 3;
        content.add(preview, gbc);

        Runnable updatePreview = () -> {
            ResourceType rt  = resTypes[resBox.getSelectedIndex()];
            int amt          = (int) amountSpinner.getValue();
            int unitSize     = (rt == ResourceType.IRON) ? 5 : 10;
            boolean hasEnough = mainController.getGameMap().getTownHall()
                    .getInventory().hasEnough(rt, amt);

            int gain;
            if (rt == ResourceType.IRON)       gain = (amt / 5)  * 3;
            else if (rt == ResourceType.STONE)  gain = (amt / 10) * 3;
            else                                gain = (amt / 10) * 2;

            if (!hasEnough) {
                preview.setText("⚠️ Not enough " + rt.name() + " in storage!");
                preview.setForeground(ACCENT_RED);
            } else if (gain <= 0) {
                preview.setText("⚠️ Minimum: " + unitSize + " " + rt.name() + " for any gain");
                preview.setForeground(ACCENT_RED);
            } else {
                int cur = tribe.getRelationship();
                int after = Math.min(100, cur + gain);
                preview.setText("Relation gain: +" + gain + "  (" + cur + " → " + after + ")");
                preview.setForeground(ACCENT_GREEN);
            }
        };

        resBox.addActionListener(e -> updatePreview.run());
        amountSpinner.addChangeListener(e -> updatePreview.run());
        updatePreview.run();

        JButton confirmBtn = buildSubButton("✅  Send Gift", true);
        gbc.gridy = 4;
        content.add(confirmBtn, gbc);

        confirmBtn.addActionListener(e -> {
            ResourceType rt  = resTypes[resBox.getSelectedIndex()];
            int          amt = (int) amountSpinner.getValue();
            if (tribeController.sendGift(tribe, rt, amt)) {
                giftDlg.dispose();
                rebuildAndRefresh();
            } else {
                preview.setText("⚠️ Not enough resources or amount too small!");
                preview.setForeground(ACCENT_RED);
            }
        });

        finalizeSubDialog(giftDlg, content);
    }

    private void showTradeDialog() {
        JDialog tradeDlg = createSubDialog("💱 Trade with " + tribe.getType().getDisplayName());
        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 4, 6, 4);

        JLabel rateInfo = makeLabel("Trade rate: " + getTribeTradeRateLabel(), ACCENT_GOLD, Font.BOLD, 13);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 3;
        content.add(rateInfo, gbc);

        ResourceType[] allRes   = {ResourceType.FOOD, ResourceType.WOOD,
                ResourceType.STONE, ResourceType.IRON};
        String[]       resEmoji = {"🍔 Food", "🪵 Wood", "🪨 Stone", "⚙️ Iron"};

        gbc.gridy = 1; gbc.gridwidth = 1;
        content.add(makeLabel("Give:", TEXT_DIM, Font.PLAIN, 12), gbc);
        JComboBox<String> giveBox = new JComboBox<>(resEmoji);
        giveBox.setBackground(BG_CARD); giveBox.setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2; content.add(giveBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        content.add(makeLabel("Amount:", TEXT_DIM, Font.PLAIN, 12), gbc);
        JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 9999, 10));
        amountSpinner.getEditor().getComponent(0).setBackground(BG_CARD);
        ((JSpinner.DefaultEditor) amountSpinner.getEditor()).getTextField().setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2; content.add(amountSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        content.add(makeLabel("Receive:", TEXT_DIM, Font.PLAIN, 12), gbc);
        JComboBox<String> getBox = new JComboBox<>(resEmoji);
        getBox.setBackground(BG_CARD); getBox.setForeground(TEXT_MAIN);
        boolean getBoxEnabled = (tribe.getType() == TribeType.COMMERCIAL);
        getBox.setEnabled(getBoxEnabled);
        if (!getBoxEnabled) {
            ResourceType fixed = getFixedReceiveResource();
            for (int i = 0; i < allRes.length; i++) {
                if (allRes[i] == fixed) { getBox.setSelectedIndex(i); break; }
            }
        }
        gbc.gridx = 1; gbc.gridwidth = 2; content.add(getBox, gbc);

        JLabel preview = makeLabel("You receive: ~10", ACCENT_GREEN, Font.BOLD, 13);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        content.add(preview, gbc);

        JButton confirmBtn = buildSubButton("✅ Confirm Trade", true);

        Runnable updatePreview = () -> {
            int amt = (int) amountSpinner.getValue();
            ResourceType getRes = allRes[getBox.getSelectedIndex()];
            ResourceType giveRes = allRes[giveBox.getSelectedIndex()];

            if (giveRes == getRes) {
                preview.setText("⚠️ Cannot trade same resource!");
                preview.setForeground(ACCENT_RED);
                confirmBtn.setEnabled(false);
                return;
            }

            Inventory inv = mainController.getGameMap().getTownHall().getInventory();
            boolean hasEnoughGive = inv.hasEnough(giveRes, amt);

            int recv = tribe.getType().getTradeStrategy()
                    .calculateReceivedAmount(amt, getRes, tribe.hasTradeBonus());

            int currentGet = inv.getResourceAmount(getRes);
            int capGet = inv.getCapacity(getRes);
            boolean hasCapacity = (currentGet + recv <= capGet);

            if (!hasEnoughGive) {
                preview.setText("⚠️ Not enough " + giveRes.name() + "!");
                preview.setForeground(ACCENT_RED);
                confirmBtn.setEnabled(false);
            } else if (!hasCapacity) {
                preview.setText("⚠️ Storage full! Cannot hold " + recv + " " + getRes.name());
                preview.setForeground(ACCENT_RED);
                confirmBtn.setEnabled(false);
            } else {
                preview.setText("You receive: ~" + recv + " " + resEmoji[getBox.getSelectedIndex()].split(" ")[1]);
                preview.setForeground(ACCENT_GREEN);
                confirmBtn.setEnabled(true);
            }
        };

        amountSpinner.addChangeListener(e -> updatePreview.run());
        getBox.addActionListener(e -> updatePreview.run());
        giveBox.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        gbc.gridy = 5; content.add(confirmBtn, gbc);

        confirmBtn.addActionListener(e -> {
            ResourceType give = allRes[giveBox.getSelectedIndex()];
            ResourceType get  = allRes[getBox.getSelectedIndex()];
            int amt = (int) amountSpinner.getValue();
            if (tribeController.tradeWithTribe(camp, give, amt, get)) {
                tradeDlg.dispose();
                rebuildAndRefresh();
            } else {
                preview.setText("⚠️ Not enough resources or storage!");
                preview.setForeground(ACCENT_RED);
            }
        });

        finalizeSubDialog(tradeDlg, content);
    }

    private void showMissionInfo() {
        JDialog dlg = createSubDialog("📜 Mission Board — " + tribe.getType().getDisplayName());
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        JTextArea ta = new JTextArea(getMissionDescription());
        ta.setBackground(BG_CARD);
        ta.setForeground(TEXT_MAIN);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(ta, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(BG_DARK);

        Mission m = tribe.getMission();
        if (m != null && m.getState().canAccept() && tribe.getRelationship() >= 20) {
            JButton acceptBtn = buildSubButton("✅ Accept Mission", true);
            acceptBtn.addActionListener(e -> {
                tribeController.acceptMission(camp);
                dlg.dispose();
                rebuildAndRefresh();
            });
            btnPanel.add(acceptBtn);
        } else if (m != null && (m.getState().getDisplayName().equals("Active")
                || m.getState().getDisplayName().equals("Ready to Deliver"))) {
            JButton cancelBtn = buildSubButton("🚫 Cancel Mission", true);
            cancelBtn.setBackground(ACCENT_RED);
            cancelBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(dlg,
                        "Cancel this mission? You will lose 5 relationship.",
                        "Cancel Mission", JOptionPane.YES_NO_OPTION);
                if (confirm == JOptionPane.YES_OPTION) {
                    tribeController.cancelMission(camp);
                    dlg.dispose();
                    rebuildAndRefresh();
                }
            });
            btnPanel.add(cancelBtn);
        }

        JButton okBtn = buildSubButton("Close", true);
        okBtn.addActionListener(e -> dlg.dispose());
        btnPanel.add(okBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        finalizeSubDialog(dlg, content);
    }

    private void showRewardsDialog() {
        JDialog dlg = createSubDialog("⭐ Tribe Rewards — " + tribe.getType().getDisplayName());
        dlg.setSize(460, 420);

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        JTextArea ta = new JTextArea(getTribeRewardDescription());
        ta.setBackground(BG_CARD);
        ta.setForeground(TEXT_MAIN);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(12, 14, 12, 14));
        content.add(new JScrollPane(ta) {{
            setBorder(null);
            getViewport().setBackground(BG_CARD);
        }}, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnPanel.setBackground(BG_DARK);
        JButton closeBtn = buildSubButton("Close", true);
        closeBtn.addActionListener(e -> dlg.dispose());
        btnPanel.add(closeBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        finalizeSubDialog(dlg, content);
    }

    // ─── MVC CLEANUP: دریافت اطلاعات از مدل به جای هاردکد کردن ───

    private String getTribeRewardDescription() {
        String currentStatus = tribe.getState().getName();
        int    currentRel    = tribe.getRelationship();

        String statusNote = switch (currentStatus) {
            case "Allied"     -> "✅ ACTIVE — You are currently allied with this tribe!";
            case "Friendly"   -> "✔ Friendly bonuses active. Reach ≥70 relation for alliance.";
            case "Neutral"    -> "Reach ≥20 relation for Friendly status.";
            case "Displeased" -> "⚠ Improve relations to Friendly (≥20) to unlock trade & missions.";
            case "Enemy"      -> "❌ At war. Make peace first before seeking friendship.";
            default           -> "";
        };

        String friendlyReward = tribe.getType().getFriendlyRewardDescription();
        String alliedReward   = tribe.getType().getAlliedRewardDescription();

        String allianceRequirement =
                "• Requires: ≥70 relation\n"
                        + "• Requires: No failed mission in last 5 turns\n"
                        + "• Requires: No conflicting alliances";

        return String.format(
                "Current Status: %s  (Relation: %d/100)\n%s\n\n"
                        + "──────── FRIENDLY STATUS (≥20 relation) ────────\n%s\n\n"
                        + "──────── ALLIED STATUS (≥70 relation) ───────────\n%s\n\n"
                        + "──────── ALLIANCE REQUIREMENTS ────────────────\n%s\n\n"
                        + "──────── HOW TO IMPROVE RELATIONS ──────────────\n"
                        + "• Send gifts: 10 Food/Wood → +2 | 10 Stone → +3 | 5 Iron → +3\n"
                        + "• Complete tribe missions: +10 to +20 per mission\n"
                        + "• Trade regularly when Friendly",
                currentStatus, currentRel, statusNote,
                friendlyReward, alliedReward, allianceRequirement
        );
    }

    private String getMissionDescription() {
        Mission m = tribe.getMission();
        if (m == null) {
            return "No mission available at the moment.\n\n"
                    + "Keep a Friendly relationship and check back later.\n"
                    + "New missions appear every 5 turns when you are Friendly.";
        }

        String stateStr = m.getState().getDisplayName();
        String turnsStr = (stateStr.equals("Active") || stateStr.equals("Ready to Deliver"))
                ? m.getTurnsRemaining() + " turns remaining" : "N/A";

        String base = m.getGoal().getDescription();

        if (tribe.getType() == TribeType.WARRIOR
                && (stateStr.equals("Active") || stateStr.equals("Ready to Deliver"))) {
            base += "\n\nProgress: " + m.getProgress() + "/2 kills";
        }

        return String.format("Status: [%s]\nTime Left: %s\n\n%s", stateStr, turnsStr, base);
    }

    private void tryFormAlliance() {
        if (tribeController.formAlliance(tribe)) {
            JOptionPane.showMessageDialog(this,
                    "Alliance formed with " + tribe.getType().getDisplayName()
                            + " Tribe!\nPermanent bonus is now active.",
                    "Alliance Formed ✅", JOptionPane.INFORMATION_MESSAGE);
            rebuildAndRefresh();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Cannot form alliance:\n" + getAllianceDisabledReason(),
                    "Alliance Denied", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void confirmDeclareWar() {
        String penalty = tribe.isAllied() || tribe.getRelationship() >= 70
                ? "⚠️ You will lose 15 Happiness (Allied tribe)!"
                : tribe.getRelationship() >= 20
                ? "⚠️ You will lose 5 Happiness (Friendly tribe)!" : "";
        int choice = JOptionPane.showConfirmDialog(this,
                "Declare war on " + tribe.getType().getDisplayName() + " Tribe?\n"
                        + "This cannot be undone easily.\n" + penalty,
                "Confirm War Declaration", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.YES_OPTION) {
            tribeController.declareWar(tribe);
            rebuildAndRefresh();
        }
    }

    private void tryRequestPeace() {
        if (tribeController.requestPeace(tribe)) {
            JOptionPane.showMessageDialog(this,
                    "Peace achieved! Status changed to Displeased.",
                    "Peace Agreed 🕊️", JOptionPane.INFORMATION_MESSAGE);
            rebuildAndRefresh();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Peace requires 30 Food + 30 Wood + 30 Iron.\nCheck your resources.",
                    "Cannot Request Peace", JOptionPane.WARNING_MESSAGE);
        }
    }

    private JButton buildActionButton(String label, String description,
                                      boolean enabled, String disabledReason,
                                      Color accentColor, Runnable action) {
        JButton btn = new JButton();
        btn.setLayout(new BorderLayout(8, 2));
        btn.setBackground(enabled ? BG_CARD : BTN_DISABLED);
        btn.setOpaque(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(enabled ? accentColor : new Color(70, 75, 85), 1),
                new EmptyBorder(10, 14, 10, 14)));
        btn.setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl  = makeLabel(label,       enabled ? TEXT_MAIN : TEXT_DIM, Font.BOLD,  14);
        JLabel desc = makeLabel(
                enabled ? description : (disabledReason != null ? "🔒 " + disabledReason : description),
                enabled ? TEXT_DIM : new Color(100, 105, 115), Font.PLAIN, 11);

        btn.add(lbl,  BorderLayout.NORTH);
        btn.add(desc, BorderLayout.CENTER);

        if (enabled && action != null) {
            btn.addActionListener(e -> action.run());
            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { btn.setBackground(BG_CARD.brighter()); }
                @Override public void mouseExited(MouseEvent e)  { btn.setBackground(BG_CARD); }
            });
        }

        if (disabledReason != null && !enabled) btn.setToolTipText(disabledReason);
        return btn;
    }

    private JButton buildSubButton(String text, boolean enabled) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btn.setBackground(enabled ? ACCENT_BLUE : BTN_DISABLED);
        btn.setForeground(TEXT_MAIN);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(8, 18, 8, 18));
        btn.setEnabled(enabled);
        btn.setCursor(enabled
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        return btn;
    }

    private JButton buildIconButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(new EmptyBorder(4, 10, 4, 10));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel makeLabel(String text, Color color, int style, int size) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", style, size));
        lbl.setForeground(color);
        return lbl;
    }

    private JDialog createSubDialog(String title) {
        JDialog dlg = new JDialog(this, title, true);
        dlg.setUndecorated(false);
        dlg.setSize(440, 380);
        dlg.setLocationRelativeTo(this);
        dlg.getContentPane().setBackground(BG_DARK);
        return dlg;
    }

    private void finalizeSubDialog(JDialog dlg, JPanel content) {
        dlg.setContentPane(content);
        dlg.pack();
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    private void rebuildAndRefresh() {
        dispose();
        if (!camp.isDestroyed()) {
            JFrame parent = (JFrame) getOwner();
            TribeInteractionDialog fresh = new TribeInteractionDialog(
                    parent, camp, mainController, onClose);
            fresh.setVisible(true);
        }
        if (onClose != null) onClose.run();
    }

    private String getTribeEmoji() {
        return switch (tribe.getType()) {
            case FARMER     -> "🌾";
            case WARRIOR    -> "⚔️";
            case MOUNTAIN   -> "⛰️";
            case COMMERCIAL -> "💰";
            case COASTAL    -> "⚓";
        };
    }

    private Color getStatusColor(String status) {
        return switch (status) {
            case "Allied"     -> ACCENT_PURP;
            case "Friendly"   -> ACCENT_GREEN;
            case "Neutral"    -> TEXT_DIM;
            case "Displeased" -> ACCENT_GOLD;
            case "Enemy"      -> ACCENT_RED;
            default           -> TEXT_MAIN;
        };
    }

    private String getTribeTradeRateLabel() {
        return switch (tribe.getType()) {
            case FARMER     -> "75% → Food";
            case MOUNTAIN   -> "75% → Stone/Iron";
            case COMMERCIAL -> "80% → Any resource";
            case COASTAL    -> "75% → Food";
            case WARRIOR    -> "No trading";
        };
    }

    private ResourceType getFixedReceiveResource() {
        return switch (tribe.getType()) {
            case FARMER, COASTAL -> ResourceType.FOOD;
            case MOUNTAIN        -> ResourceType.STONE;
            default              -> ResourceType.WOOD;
        };
    }

    private String getAllianceDisabledReason() {
        if (!tribe.canFormAlliance())
            return "Requires ≥70 relation (current: " + tribe.getRelationship() + ")";

        if (tribe.getMissionCooldown() > 0)
            return "A mission failed recently — wait " + tribe.getMissionCooldown()
                    + " more turn(s) before requesting alliance";

        for (Hex h : mainController.getGameMap().getHexes()) {
            if (!(h.getBuilding() instanceof TribeCamp)) continue;
            if (h.getBuilding().isDestroyed()) continue;
            Tribe t = ((TribeCamp) h.getBuilding()).getTribe();
            if (!t.isAllied() || t == tribe) continue;

            if (t.getType() == TribeType.WARRIOR)
                return "Warrior tribe doesn't allow other alliances";
            if (tribe.getType() == TribeType.WARRIOR)
                return "Warrior tribe cannot ally alongside other tribes";
            if (t.getType() == TribeType.FARMER && tribe.getType() == TribeType.MOUNTAIN)
                return "Farmer and Mountain tribes cannot be allied simultaneously";
            if (t.getType() == TribeType.MOUNTAIN && tribe.getType() == TribeType.FARMER)
                return "Mountain and Farmer tribes cannot be allied simultaneously";
        }
        return null;
    }
}