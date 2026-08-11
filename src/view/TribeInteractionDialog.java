package view;

import controller.MainController;
import controller.TribeController;
import model.*;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Dialog تعامل با قبیله — F-05.
 *
 * ۸ گزینه تعامل طبق spec:
 * 1. ارسال هدیه        (همه وضعیت‌های غیر دشمن)
 * 2. شروع تجارت        (≥20 رابطه)
 * 3. دریافت مأموریت    (≥20 رابطه، بدون مأموریت فعال)
 * 4. تحویل مأموریت    (فقط وقتی شرط برآورده شده)
 * 5. اعلام جنگ         (همه وضعیت‌های غیر دشمن؛ confirmation اجباری)
 * 6. درخواست صلح       (فقط در حالت دشمن)
 * 7. درخواست اتحاد     (≥70 رابطه)
 * 8. مشاهده bonus ها   (همیشه)
 */
public class TribeInteractionDialog extends JDialog {

    // رنگ‌های اصلی UI
    private static final Color BG_DARK      = new Color(25, 28, 35);
    private static final Color BG_CARD      = new Color(35, 39, 48);
    private static final Color ACCENT_BLUE  = new Color(52, 152, 219);
    private static final Color ACCENT_GREEN = new Color(46, 204, 113);
    private static final Color ACCENT_RED   = new Color(231, 76, 60);
    private static final Color ACCENT_GOLD  = new Color(241, 196, 15);
    private static final Color ACCENT_PURP  = new Color(155, 89, 182);
    private static final Color TEXT_MAIN    = new Color(236, 240, 241);
    private static final Color TEXT_DIM     = new Color(127, 140, 141);
    private static final Color BTN_DISABLED = new Color(60, 65, 75);

    private final TribeCamp      camp;
    private final Tribe          tribe;
    private final TribeController tribeController;
    private final MainController mainController;
    private final Runnable       onClose;

    public TribeInteractionDialog(JFrame parent, TribeCamp camp,
                                  MainController mainController, Runnable onClose) {
        super(parent, "Tribe Interaction", true);
        this.camp            = camp;
        this.tribe           = camp.getTribe();
        this.tribeController = mainController.getTribeController();
        this.mainController  = mainController;
        this.onClose         = onClose;

        setUndecorated(true);
        setSize(480, 620);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_BLUE, 2));

        root.add(buildHeader(), BorderLayout.NORTH);
        root.add(buildScrollableActions(), BorderLayout.CENTER);
        root.add(buildFooter(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    // ─── Header — اطلاعات قبیله ──────────────────────────────────────────────

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(12, 8));
        panel.setBackground(BG_CARD);
        panel.setBorder(new EmptyBorder(16, 20, 16, 20));

        // آیکون و نام
        JLabel title = new JLabel(getTribeEmoji() + "  " + tribe.getType().getDisplayName() + " Tribe");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(TEXT_MAIN);

        // وضعیت رابطه
        Color statusColor = getStatusColor(tribe.getStatus());
        JLabel statusLbl = new JLabel(tribe.getDetailedStatus());
        statusLbl.setFont(new Font("Segoe UI", Font.BOLD, 13));
        statusLbl.setForeground(statusColor);

        // نوار رابطه بصری
        JPanel relationBar = buildRelationBar();

        JPanel textPanel = new JPanel(new GridLayout(3, 1, 2, 4));
        textPanel.setOpaque(false);
        textPanel.add(title);
        textPanel.add(statusLbl);
        textPanel.add(relationBar);

        panel.add(textPanel, BorderLayout.CENTER);

        // دکمه بستن
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
                // پس‌زمینه
                g2.setColor(new Color(50, 55, 65));
                g2.fillRoundRect(0, h/3, w, h/3, 4, 4);

                // مقدار رابطه (-100 تا +100 → 0 تا w)
                int rel      = tribe.getRelationship();
                int fillW    = (int)((rel + 100) / 200.0 * w);
                Color fillC  = getStatusColor(tribe.getStatus());
                g2.setColor(fillC);
                g2.fillRoundRect(0, h/3, fillW, h/3, 4, 4);

                // خط وسط (صفر)
                g2.setColor(TEXT_DIM);
                g2.drawLine(w/2, h/4, w/2, 3*h/4);
            }
        };
        bar.setOpaque(false);
        bar.setPreferredSize(new Dimension(0, 18));
        return bar;
    }

    // ─── محتوای actions ──────────────────────────────────────────────────────

    private JScrollPane buildScrollableActions() {
        JPanel actionsPanel = new JPanel();
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));
        actionsPanel.setBackground(BG_DARK);
        actionsPanel.setBorder(new EmptyBorder(12, 16, 12, 16));

        // ۱. ارسال هدیه
        actionsPanel.add(buildActionButton(
                "🎁  Send Gift",
                "Send resources to improve relations (+2 or +3)",
                tribe.canReceiveGift(),
                tribe.canReceiveGift() ? null : "Cannot gift to an enemy tribe",
                ACCENT_GREEN,
                this::showGiftDialog));

        // ۲. شروع تجارت
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        boolean canTrade = tribe.canTrade() && !camp.hasTraded();
        actionsPanel.add(buildActionButton(
                "💱  Trade Resources",
                "Exchange resources at " + getTribeTradeRateLabel(),
                canTrade,
                !tribe.canTrade() ? "Requires Friendly status (≥20 relation)"
                        : "Already traded this turn",
                ACCENT_BLUE,
                this::showTradeDialog));

        // ۳. دریافت مأموریت
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        boolean canMission = tribe.canTrade();
        actionsPanel.add(buildActionButton(
                "📜  Request Mission",
                "Complete a task for resources and relation boost",
                canMission,
                "Requires Friendly status (≥20 relation)",
                ACCENT_GOLD,
                this::showMissionInfo));

        // ۴. تحویل مأموریت (placeholder تا پیاده‌سازی کامل mission)
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        actionsPanel.add(buildActionButton(
                "✅  Deliver Mission",
                "Deliver completed mission for rewards",
                false,
                "No active mission to deliver",
                ACCENT_GOLD,
                null));

        // ۵. درخواست اتحاد
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        boolean canAlliance = tribe.canFormAlliance();
        String allianceReason = getAllianceDisabledReason();
        actionsPanel.add(buildActionButton(
                "🤝  Request Alliance",
                "Form a permanent alliance for ongoing bonuses",
                canAlliance && allianceReason == null,
                allianceReason != null ? allianceReason
                        : (canAlliance ? null : "Requires Allied status (≥70 relation)"),
                ACCENT_PURP,
                this::tryFormAlliance));

        // ۶. اعلام جنگ
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        boolean canWar = !tribe.getStatus().equals("Enemy");
        actionsPanel.add(buildActionButton(
                "⚔️  Declare War",
                "Start a war — causes happiness penalty!",
                canWar,
                "Already at war with this tribe",
                ACCENT_RED,
                this::confirmDeclareWar));

        // ۷. درخواست صلح
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        actionsPanel.add(buildActionButton(
                "🕊️  Request Peace",
                "End war (costs 30 Food + 30 Wood + 30 Iron)",
                tribe.canRequestPeace(),
                "Only available when at war",
                new Color(100, 180, 255),
                this::tryRequestPeace));

        // ۸. مشاهده bonus ها (همیشه فعال)
        actionsPanel.add(Box.createRigidArea(new Dimension(0, 8)));
        actionsPanel.add(buildActionButton(
                "📊  View Tribe Bonuses",
                "See what bonuses this tribe provides when allied",
                true,
                null,
                TEXT_DIM,
                this::showBonusInfo));

        JScrollPane scroll = new JScrollPane(actionsPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        return scroll;
    }

    // ─── Footer ───────────────────────────────────────────────────────────────

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

    // ─── Gift Dialog ─────────────────────────────────────────────────────────

    private void showGiftDialog() {
        JDialog giftDlg = createSubDialog("🎁 Send Gift");

        JPanel content = new JPanel(new GridBagLayout());
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(16, 20, 16, 20));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 4, 4, 4);

        JLabel info = makeLabel("Choose resource to gift:", TEXT_MAIN, Font.BOLD, 13);
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        content.add(info, gbc);

        // طبق spec: 10F/W → +2، 10S → +3، 5I → +3
        ResourceType[] types   = {ResourceType.FOOD, ResourceType.WOOD,
                ResourceType.STONE, ResourceType.IRON};
        String[]       labels  = {"10 Food → +2 relation", "10 Wood → +2 relation",
                "10 Stone → +3 relation", "5 Iron → +3 relation"};
        String[]       emojis  = {"🍔", "🪵", "🪨", "⚙️"};

        for (int i = 0; i < types.length; i++) {
            final ResourceType rt = types[i];
            int needed = (rt == ResourceType.IRON) ? 5 : 10;
            boolean hasEnough = mainController.getGameMap().getTownHall()
                    .getInventory().hasEnough(rt, needed);

            JButton btn = buildSubButton(emojis[i] + " " + labels[i], hasEnough);
            if (hasEnough) {
                btn.addActionListener(e -> {
                    if (tribeController.sendGift(tribe, rt)) {
                        giftDlg.dispose();
                        rebuildAndRefresh();
                    }
                });
            }
            gbc.gridy = i + 1; gbc.gridwidth = 2;
            content.add(btn, gbc);
        }

        finalizeSubDialog(giftDlg, content);
    }

    // ─── Trade Dialog ─────────────────────────────────────────────────────────

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

        // Give resource
        gbc.gridy = 1; gbc.gridwidth = 1;
        content.add(makeLabel("Give:", TEXT_DIM, Font.PLAIN, 12), gbc);

        ResourceType[] allRes = {ResourceType.FOOD, ResourceType.WOOD,
                ResourceType.STONE, ResourceType.IRON};
        String[] resEmoji = {"🍔 Food", "🪵 Wood", "🪨 Stone", "⚙️ Iron"};

        JComboBox<String> giveBox = new JComboBox<>(resEmoji);
        giveBox.setBackground(BG_CARD);
        giveBox.setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2;
        content.add(giveBox, gbc);

        // Amount
        gbc.gridx = 0; gbc.gridy = 2; gbc.gridwidth = 1;
        content.add(makeLabel("Amount:", TEXT_DIM, Font.PLAIN, 12), gbc);

        JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 9999, 10));
        amountSpinner.getEditor().getComponent(0).setBackground(BG_CARD);
        ((JSpinner.DefaultEditor) amountSpinner.getEditor()).getTextField().setForeground(TEXT_MAIN);
        gbc.gridx = 1; gbc.gridwidth = 2;
        content.add(amountSpinner, gbc);

        // Get resource (فقط برای COMMERCIAL آزاد است)
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 1;
        content.add(makeLabel("Receive:", TEXT_DIM, Font.PLAIN, 12), gbc);

        JComboBox<String> getBox = new JComboBox<>(resEmoji);
        getBox.setBackground(BG_CARD);
        getBox.setForeground(TEXT_MAIN);
        boolean getBoxEnabled = (tribe.getType() == TribeType.COMMERCIAL);
        getBox.setEnabled(getBoxEnabled);
        if (!getBoxEnabled) {
            // نوع دریافتی ثابت است
            ResourceType fixed = getFixedReceiveResource();
            for (int i = 0; i < allRes.length; i++) {
                if (allRes[i] == fixed) { getBox.setSelectedIndex(i); break; }
            }
        }
        gbc.gridx = 1; gbc.gridwidth = 2;
        content.add(getBox, gbc);

        // پیش‌نمایش دریافتی
        JLabel preview = makeLabel("You receive: ~10", ACCENT_GREEN, Font.BOLD, 13);
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 3;
        content.add(preview, gbc);

        // به‌روز کردن preview
        Runnable updatePreview = () -> {
            int    amt  = (int) amountSpinner.getValue();
            double rate = getTradeRateForSelected(allRes[getBox.getSelectedIndex()]);
            int    recv = (int) Math.floor(amt * rate);
            preview.setText("You receive: ~" + recv + " "
                    + resEmoji[getBox.getSelectedIndex()].split(" ")[1]);
        };
        amountSpinner.addChangeListener(e -> updatePreview.run());
        getBox.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        // دکمه تأیید
        JButton confirmBtn = buildSubButton("✅ Confirm Trade", true);
        gbc.gridy = 5;
        content.add(confirmBtn, gbc);

        confirmBtn.addActionListener(e -> {
            ResourceType give  = allRes[giveBox.getSelectedIndex()];
            ResourceType get   = allRes[getBox.getSelectedIndex()];
            int          amt   = (int) amountSpinner.getValue();
            if (tribeController.tradeWithTribe(camp, give, amt, get)) {
                tradeDlg.dispose();
                rebuildAndRefresh();
            } else {
                preview.setText("⚠️ Not enough resources!");
                preview.setForeground(ACCENT_RED);
            }
        });

        finalizeSubDialog(tradeDlg, content);
    }

    // ─── Mission Info (placeholder) ───────────────────────────────────────────

    private void showMissionInfo() {
        JDialog dlg = createSubDialog("📜 Mission — " + tribe.getType().getDisplayName());
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        String missionText = getMissionDescription();
        JTextArea ta = new JTextArea(missionText);
        ta.setBackground(BG_CARD);
        ta.setForeground(TEXT_MAIN);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(12, 12, 12, 12));

        content.add(ta, BorderLayout.CENTER);

        JButton okBtn = buildSubButton("OK", true);
        okBtn.addActionListener(e -> dlg.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG_DARK);
        btnPanel.add(okBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        finalizeSubDialog(dlg, content);
    }

    // ─── Alliance ─────────────────────────────────────────────────────────────

    private void tryFormAlliance() {
        if (tribeController.formAlliance(tribe)) {
            JOptionPane.showMessageDialog(this,
                    "Alliance formed with " + tribe.getType().getDisplayName() + " Tribe!\n"
                            + "Permanent bonus is now active.",
                    "Alliance Formed ✅",
                    JOptionPane.INFORMATION_MESSAGE);
            rebuildAndRefresh();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Cannot form alliance:\n" + getAllianceDisabledReason(),
                    "Alliance Denied",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ─── Declare War ──────────────────────────────────────────────────────────

    private void confirmDeclareWar() {
        String penalty = tribe.isAllied() || tribe.getRelationship() >= 70
                ? "⚠️ You will lose 15 Happiness (Allied tribe)!"
                : tribe.getRelationship() >= 20
                ? "⚠️ You will lose 5 Happiness (Friendly tribe)!"
                : "";

        int choice = JOptionPane.showConfirmDialog(this,
                "Declare war on " + tribe.getType().getDisplayName() + " Tribe?\n"
                        + "This cannot be undone easily.\n"
                        + penalty,
                "Confirm War Declaration",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (choice == JOptionPane.YES_OPTION) {
            tribeController.declareWar(tribe);
            rebuildAndRefresh();
        }
    }

    // ─── Peace ────────────────────────────────────────────────────────────────

    private void tryRequestPeace() {
        if (tribeController.requestPeace(tribe)) {
            JOptionPane.showMessageDialog(this,
                    "Peace achieved! Status changed to Displeased.",
                    "Peace Agreed 🕊️",
                    JOptionPane.INFORMATION_MESSAGE);
            rebuildAndRefresh();
        } else {
            JOptionPane.showMessageDialog(this,
                    "Peace requires 30 Food + 30 Wood + 30 Iron.\nCheck your resources.",
                    "Cannot Request Peace",
                    JOptionPane.WARNING_MESSAGE);
        }
    }

    // ─── Bonus Info ───────────────────────────────────────────────────────────

    private void showBonusInfo() {
        JDialog dlg = createSubDialog("📊 Tribe Bonuses — " + tribe.getType().getDisplayName());
        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBackground(BG_DARK);
        content.setBorder(new EmptyBorder(20, 24, 20, 24));

        JTextArea ta = new JTextArea(getBonusDescription());
        ta.setBackground(BG_CARD);
        ta.setForeground(TEXT_MAIN);
        ta.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        ta.setEditable(false);
        ta.setLineWrap(true);
        ta.setWrapStyleWord(true);
        ta.setBorder(new EmptyBorder(12, 12, 12, 12));
        content.add(ta, BorderLayout.CENTER);

        JButton okBtn = buildSubButton("OK", true);
        okBtn.addActionListener(e -> dlg.dispose());
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btnPanel.setBackground(BG_DARK);
        btnPanel.add(okBtn);
        content.add(btnPanel, BorderLayout.SOUTH);

        finalizeSubDialog(dlg, content);
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    /** ساخت یک action button با label، توضیح، وضعیت و callback. */
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
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 64));
        btn.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lbl = makeLabel(label, enabled ? TEXT_MAIN : TEXT_DIM, Font.BOLD, 14);
        JLabel desc = makeLabel(
                enabled ? description : (disabledReason != null ? "🔒 " + disabledReason : description),
                enabled ? TEXT_DIM : new Color(100, 105, 115), Font.PLAIN, 11);

        btn.add(lbl, BorderLayout.NORTH);
        btn.add(desc, BorderLayout.CENTER);

        if (enabled && action != null) {
            btn.addActionListener(e -> action.run());
            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) {
                    btn.setBackground(BG_CARD.brighter());
                }
                @Override public void mouseExited(MouseEvent e) {
                    btn.setBackground(BG_CARD);
                }
            });
        }

        // Tooltip
        if (disabledReason != null && !enabled) {
            btn.setToolTipText(disabledReason);
        }

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
                ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                : Cursor.getDefaultCursor());
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
        dlg.setSize(420, 360);
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

    /** بعد از هر تعامل، dialog را بازسازی می‌کند تا وضعیت جدید نمایش داده شود. */
    private void rebuildAndRefresh() {
        dispose();
        // اگر کمپ هنوز زنده است، dialog را مجدداً باز می‌کنیم
        if (!camp.isDestroyed()) {
            JFrame parent = (JFrame) getOwner();
            TribeInteractionDialog fresh = new TribeInteractionDialog(
                    parent, camp, mainController, onClose);
            fresh.setVisible(true);
        }
        if (onClose != null) onClose.run();
    }

    // ─── Data helpers ─────────────────────────────────────────────────────────

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

    private double getTradeRateForSelected(ResourceType get) {
        return switch (tribe.getType()) {
            case FARMER     -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case MOUNTAIN   -> (get == ResourceType.STONE || get == ResourceType.IRON) ? 0.75 : 0.0;
            case COMMERCIAL -> 0.80;
            case COASTAL    -> (get == ResourceType.FOOD) ? 0.75 : 0.0;
            case WARRIOR    -> 0.0;
        };
    }

    private String getAllianceDisabledReason() {
        if (!tribe.canFormAlliance()) {
            return "Requires ≥70 relation (current: " + tribe.getRelationship() + ")";
        }
        // بررسی محدودیت‌های همزمانی
        for (Hex h : mainController.getGameMap().getHexes()) {
            if (!(h.getBuilding() instanceof TribeCamp)) continue;
            if (h.getBuilding().isDestroyed()) continue;
            Tribe t = ((TribeCamp) h.getBuilding()).getTribe();
            if (!t.isAllied() || t == tribe) continue;

            if (t.getType() == TribeType.WARRIOR) return "Warrior tribe doesn't allow other alliances";
            if (tribe.getType() == TribeType.WARRIOR)
                return "Warrior tribe cannot ally alongside other tribes";
            if (t.getType() == TribeType.FARMER && tribe.getType() == TribeType.MOUNTAIN)
                return "Farmer and Mountain tribes cannot be allied simultaneously";
            if (t.getType() == TribeType.MOUNTAIN && tribe.getType() == TribeType.FARMER)
                return "Mountain and Farmer tribes cannot be allied simultaneously";
        }
        return null;
    }

    private String getMissionDescription() {
        return switch (tribe.getType()) {
            case FARMER -> """
                    Mission: Build Food Storage
                    ─────────────────────────
                    Requirement: Pay 20 Wood + 10 Stone to the tribe.
                    Time Limit:  5 turns
                    Reward:      30 Food + 15 relation
                    
                    [Mission system coming in future update]
                    """;
            case COMMERCIAL -> """
                    Mission: Connect Trade Route
                    ─────────────────────────
                    Requirement: Build a continuous road from one of
                    your buildings to a hex adjacent to this camp.
                    Time Limit:  10 turns
                    Reward:      +10% trade rate + 20 relation
                    
                    [Mission system coming in future update]
                    """;
            case WARRIOR -> """
                    Mission: Military Aid
                    ─────────────────────────
                    Requirement: Defeat 2 enemy or barbarian units
                    within 5 hexes of this camp.
                    Time Limit:  8 turns
                    Reward:      3 Swordsmen + 20 relation
                    
                    [Mission system coming in future update]
                    """;
            case MOUNTAIN -> """
                    Mission: Mining Tools
                    ─────────────────────────
                    Requirement: Pay 15 Wood + 10 Iron to the tribe.
                    Time Limit:  6 turns
                    Reward:      20 Stone + 15 relation
                    
                    [Mission system coming in future update]
                    """;
            case COASTAL -> """
                    Mission: Coastal Development
                    ─────────────────────────
                    Requirement: Build a Dock within 4 hexes of this camp.
                    Time Limit:  10 turns
                    Reward:      30 Food + reduced Dock cost
                    
                    [Mission system coming in future update]
                    """;
        };
    }

    private String getBonusDescription() {
        return switch (tribe.getType()) {
            case FARMER -> """
                    🌾 FARMER TRIBE BONUSES
                    ════════════════════════════
                    Trade:   Any resource → Food (75% rate)
                    Allied:  +5 Food per turn
                    
                    Restrictions:
                    • Cannot ally with Mountain tribe simultaneously
                    """;
            case WARRIOR -> """
                    ⚔️ WARRIOR TRIBE BONUSES
                    ════════════════════════════
                    Trade:   No trading available
                    Allied:  Attack bonus for units near this camp
                    
                    Restrictions:
                    • Cannot ally with ANY other tribe simultaneously
                    """;
            case MOUNTAIN -> """
                    ⛰️ MOUNTAIN TRIBE BONUSES
                    ════════════════════════════
                    Trade:   Any resource → Stone or Iron (75% rate)
                    Allied:  +5 Stone per turn
                    
                    Restrictions:
                    • Cannot ally with Farmer tribe simultaneously
                    """;
            case COMMERCIAL -> """
                    💰 COMMERCIAL TRIBE BONUSES
                    ════════════════════════════
                    Trade:   Any resource → Any resource (80% rate)
                    Allied:  +10% trade rate on all trades
                    
                    Restrictions:
                    • None (most flexible tribe)
                    """;
            case COASTAL -> """
                    ⚓ COASTAL TRIBE BONUSES
                    ════════════════════════════
                    Trade:   Any resource → Food (75% rate)
                    Allied:  +3 Food per turn + Dock production boost
                    
                    Restrictions:
                    • None
                    """;
        };
    }
}