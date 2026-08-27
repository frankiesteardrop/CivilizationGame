package view;

import controller.SaveLoadController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class LoadGameDialog extends JDialog {

    private static final Color BG_DARK        = new Color(15, 17, 24);
    private static final Color BG_CARD        = new Color(28, 32, 42);
    private static final Color BG_CARD_HOVER  = new Color(35, 40, 54);
    private static final Color BG_CARD_SEL    = new Color(22, 44, 65);
    private static final Color BG_AUTOSAVE    = new Color(18, 32, 22);
    private static final Color BG_AUTO_HOVER  = new Color(22, 42, 28);
    private static final Color BG_AUTO_SEL    = new Color(18, 50, 28);
    private static final Color BG_EMPTY       = new Color(20, 22, 30);
    private static final Color ACCENT_GOLD    = new Color(220, 185, 45);
    private static final Color ACCENT_BLUE    = new Color(52, 130, 215);
    private static final Color ACCENT_GREEN   = new Color(46, 180, 80);
    private static final Color ACCENT_AUTOSAVE= new Color(60, 190, 100);
    private static final Color BORDER_NORMAL  = new Color(42, 48, 62);
    private static final Color BORDER_SEL     = new Color(52, 130, 215);
    private static final Color BORDER_AUTO    = new Color(35, 90, 52);
    private static final Color TEXT_MAIN      = new Color(225, 230, 240);
    private static final Color TEXT_DIM       = new Color(120, 130, 148);
    private static final Color TEXT_EMPTY     = new Color(70, 75, 90);
    private static final Color TEXT_META      = new Color(160, 170, 188);
    private static final Color CORRUPT_BG     = new Color(38, 18, 18);
    private static final Color CORRUPT_BORDER = new Color(140, 30, 30);
    private static final Color BTN_LOAD       = new Color(35, 100, 175);
    private static final Color BTN_CANCEL     = new Color(45, 50, 65);

    private static final String[] SLOT_KEYS   = {"autosave", "slot1", "slot2", "slot3"};
    private static final String[] SLOT_LABELS = {"🔄  Autosave", "📁  Slot 1", "📁  Slot 2", "📁  Slot 3"};

    private String        selectedSlot    = null;  // null = cancelled
    private JPanel        selectedCard    = null;
    private String        selectedKey     = null;
    private final JButton loadBtn;
    private final JPanel  slotListPanel;


    public LoadGameDialog(Frame parent) {
        super(parent, "Load Game", true);
        setUndecorated(true);
        setSize(540, 620);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_GOLD, 2));

        root.add(buildHeader(), BorderLayout.NORTH);

        slotListPanel = new JPanel();
        slotListPanel.setLayout(new BoxLayout(slotListPanel, BoxLayout.Y_AXIS));
        slotListPanel.setBackground(BG_DARK);
        slotListPanel.setBorder(new EmptyBorder(12, 16, 8, 16));

        for (int i = 0; i < SLOT_KEYS.length; i++) {
            slotListPanel.add(buildSlotCard(SLOT_KEYS[i], SLOT_LABELS[i], i == 0));
            if (i < SLOT_KEYS.length - 1) {
                slotListPanel.add(Box.createRigidArea(new Dimension(0, 8)));
            }
        }

        JScrollPane scroll = new JScrollPane(slotListPanel);
        scroll.setBorder(null);
        scroll.setBackground(BG_DARK);
        scroll.getViewport().setBackground(BG_DARK);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        root.add(scroll, BorderLayout.CENTER);

        loadBtn = new JButton("📂   Load Selected");
        JButton cancelBtn = new JButton("✕   Cancel");
        root.add(buildFooter(loadBtn, cancelBtn), BorderLayout.SOUTH);

        setContentPane(root);

        // Load başlangıçta devre dışı
        loadBtn.setEnabled(false);

        // Escape ile kapat
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }


    public String getSelectedSlot() {
        return selectedSlot;
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout(8, 0));
        panel.setBackground(new Color(18, 21, 30));
        panel.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel title = new JLabel("📂  LOAD GAME");
        title.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 22));
        title.setForeground(ACCENT_GOLD);
        panel.add(title, BorderLayout.WEST);

        JLabel hint = new JLabel("Click to select, double-click to load");
        hint.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 11));
        hint.setForeground(TEXT_DIM);
        panel.add(hint, BorderLayout.EAST);

        // خط جداکننده پایین
        JPanel sep = new JPanel();
        sep.setBackground(new Color(ACCENT_GOLD.getRed(), ACCENT_GOLD.getGreen(), ACCENT_GOLD.getBlue(), 80));
        sep.setPreferredSize(new Dimension(0, 1));
        panel.add(sep, BorderLayout.SOUTH);

        return panel;
    }


    private JPanel buildSlotCard(String slotKey, String slotLabel, boolean isAutosave) {
        SaveLoadController.SaveMetadata meta = SaveLoadController.readSlotMetadata(slotKey);

        Color baseBg    = isAutosave ? BG_AUTOSAVE : BG_CARD;
        Color hoverBg   = isAutosave ? BG_AUTO_HOVER : BG_CARD_HOVER;
        Color selBg     = isAutosave ? BG_AUTO_SEL : BG_CARD_SEL;
        Color baseBorder = isAutosave ? BORDER_AUTO : BORDER_NORMAL;
        Color titleColor = isAutosave ? ACCENT_AUTOSAVE : ACCENT_GOLD;

        JPanel card = new JPanel(new BorderLayout(10, 4));
        card.setBackground(baseBg);
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(baseBorder, 1),
                new EmptyBorder(13, 16, 13, 16)
        ));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        card.setMinimumSize(new Dimension(0, 88));

        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setOpaque(false);

        JLabel titleLbl = new JLabel(slotLabel);
        titleLbl.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
        titleLbl.setForeground(titleColor);
        titleLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
        infoPanel.add(titleLbl);
        infoPanel.add(Box.createRigidArea(new Dimension(0, 5)));

        if (meta == null || meta.isEmpty) {
            card.setBackground(BG_EMPTY);
            titleLbl.setForeground(TEXT_DIM);
            JLabel emptyLbl = new JLabel("[ EMPTY — No save file ]");
            emptyLbl.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.ITALIC, 12));
            emptyLbl.setForeground(TEXT_EMPTY);
            emptyLbl.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(emptyLbl);

        } else if ("Legacy".equals(meta.season)) {
            JPanel row1 = buildMetaRow(
                    buildMetaPill("v1.x", new Color(100, 90, 40)),
                    (meta.turnNumber > 0) ? buildMetaItem("⏳", "Turn " + meta.turnNumber) : null,
                    buildMetaItem("🕐", meta.saveTime)
            );
            row1.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(row1);

            JLabel legacyNote = new JLabel("Old format — some details unavailable");
            legacyNote.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.ITALIC, 10));
            legacyNote.setForeground(TEXT_DIM);
            legacyNote.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(Box.createRigidArea(new Dimension(0, 3)));
            infoPanel.add(legacyNote);

        } else {
            JPanel row1 = buildMetaRow(
                    buildMetaItem("⏳", "Turn " + meta.turnNumber),
                    buildMetaItem(getSeasonEmoji(meta.season), meta.season),
                    buildMetaItem("🏰", "TH Level " + meta.thLevel)
            );
            row1.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(row1);

            infoPanel.add(Box.createRigidArea(new Dimension(0, 3)));

            JPanel rowSummary = buildMetaRow(
                    buildMetaItem("📊", meta.gameSummary)
            );
            rowSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(rowSummary);

            infoPanel.add(Box.createRigidArea(new Dimension(0, 3)));

            JPanel row2 = buildMetaRow(
                    buildMetaItem("🕐", meta.saveTime),
                    buildMetaPill("v" + meta.saveVersion, new Color(40, 60, 100))
            );
            row2.setAlignmentX(Component.LEFT_ALIGNMENT);
            infoPanel.add(row2);
        }

        card.add(infoPanel, BorderLayout.CENTER);

        boolean canLoad = (meta != null && !meta.isEmpty);
        JButton loadCardBtn = buildCardLoadButton(canLoad);
        if (canLoad) {
            loadCardBtn.addActionListener(e -> loadSlot(slotKey));
        }

        JPanel rightPanel = new JPanel(new GridBagLayout());
        rightPanel.setOpaque(false);
        rightPanel.add(loadCardBtn);
        card.add(rightPanel, BorderLayout.EAST);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!canLoad) return;
                selectCard(card, slotKey, baseBg, selBg, baseBorder, isAutosave);

                if (e.getClickCount() == 2) {
                    loadSlot(slotKey);
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if (!canLoad) return;
                if (selectedCard != card) card.setBackground(hoverBg);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (!canLoad) return;
                if (selectedCard != card) card.setBackground(baseBg);
            }
        });

        propagateMouseToCard(infoPanel, card, canLoad, slotKey, baseBg, selBg, hoverBg, baseBorder, isAutosave);

        return card;
    }

    private void selectCard(JPanel card, String slotKey,
                            Color baseBg, Color selBg, Color baseBorder, boolean isAutosave) {

        if (selectedCard != null && selectedCard != card) {
            selectedCard.setBackground(isAutosave ? BG_AUTOSAVE : BG_CARD);
            selectedCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isAutosave ? BORDER_AUTO : BORDER_NORMAL, 1),
                    new EmptyBorder(13, 16, 13, 16)
            ));
        }

        selectedCard = card;
        selectedKey  = slotKey;

        card.setBackground(selBg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SEL, 2),
                new EmptyBorder(12, 15, 12, 15)
        ));

        loadBtn.setEnabled(true);
        loadBtn.setBackground(BTN_LOAD);
        loadBtn.setText("📂   Load  — " + slotKey);
    }

    private void loadSlot(String slotKey) {
        selectedSlot = slotKey;
        dispose();
    }

    private JPanel buildFooter(JButton loadBtn, JButton cancelBtn) {
        JPanel panel = new JPanel(new BorderLayout(0, 0));
        panel.setBackground(new Color(18, 21, 30));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(42, 48, 62)),
                new EmptyBorder(12, 20, 14, 20)
        ));
        loadBtn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
        loadBtn.setBackground(new Color(35, 45, 65));
        loadBtn.setForeground(TEXT_DIM);
        loadBtn.setFocusPainted(false);
        loadBtn.setOpaque(true);
        loadBtn.setBorderPainted(false);
        loadBtn.setBorder(new EmptyBorder(10, 24, 10, 24));
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadBtn.addActionListener(e -> {
            if (selectedKey != null) loadSlot(selectedKey);
        });
        loadBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                if (loadBtn.isEnabled()) loadBtn.setBackground(BTN_LOAD.brighter());
            }
            @Override public void mouseExited(MouseEvent e)  {
                if (loadBtn.isEnabled()) loadBtn.setBackground(BTN_LOAD);
            }
        });

        cancelBtn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        cancelBtn.setBackground(BTN_CANCEL);
        cancelBtn.setForeground(TEXT_DIM);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setOpaque(true);
        cancelBtn.setBorderPainted(false);
        cancelBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dispose());
        cancelBtn.addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) { cancelBtn.setBackground(BTN_CANCEL.brighter()); }
            @Override public void mouseExited(MouseEvent e)  { cancelBtn.setBackground(BTN_CANCEL); }
        });

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(cancelBtn);
        btnRow.add(loadBtn);
        panel.add(btnRow, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildMetaRow(JComponent... items) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        row.setOpaque(false);
        for (JComponent item : items) {
            if (item != null) row.add(item);
        }
        return row;
    }

    private JLabel buildMetaItem(String icon, String text) {
        JLabel lbl = new JLabel(icon + "  " + text);
        lbl.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 12));
        lbl.setForeground(TEXT_META);
        lbl.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(48, 55, 72), 1),
                new EmptyBorder(2, 7, 2, 7)
        ));
        lbl.setOpaque(true);
        lbl.setBackground(new Color(32, 37, 50));
        return lbl;
    }


    private JLabel buildMetaPill(String text, Color bg) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 10));
        lbl.setForeground(new Color(200, 210, 230));
        lbl.setBorder(new EmptyBorder(2, 7, 2, 7));
        lbl.setOpaque(true);
        lbl.setBackground(bg);
        return lbl;
    }

    private JButton buildCardLoadButton(boolean enabled) {
        JButton btn = new JButton(enabled ? "Load" : "—");
        btn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 12));
        btn.setBackground(enabled ? new Color(35, 85, 145) : new Color(32, 35, 45));
        btn.setForeground(enabled ? Color.WHITE : TEXT_EMPTY);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBorder(new EmptyBorder(7, 14, 7, 14));
        btn.setEnabled(enabled);
        btn.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (enabled) {
            btn.addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { btn.setBackground(new Color(45, 110, 180)); }
                @Override public void mouseExited(MouseEvent e)  { btn.setBackground(new Color(35, 85, 145)); }
            });
        }
        return btn;
    }

    private void propagateMouseToCard(JPanel source, JPanel card, boolean canLoad,
                                      String slotKey, Color baseBg, Color selBg,
                                      Color hoverBg, Color baseBorder, boolean isAutosave) {
        MouseAdapter propagator = new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                if (!canLoad) return;
                selectCard(card, slotKey, baseBg, selBg, baseBorder, isAutosave);
                if (e.getClickCount() == 2) loadSlot(slotKey);
            }
            @Override public void mouseEntered(MouseEvent e) {
                if (!canLoad) return;
                if (selectedCard != card) card.setBackground(hoverBg);
            }
            @Override public void mouseExited(MouseEvent e) {
                if (!canLoad) return;
                if (selectedCard != card) card.setBackground(baseBg);
            }
        };

        for (Component child : source.getComponents()) {
            child.addMouseListener(propagator);
        }
    }

    private String getSeasonEmoji(String season) {
        return switch (season) {
            case "SPRING" -> "🌸";
            case "SUMMER" -> "☀️";
            case "AUTUMN" -> "🍂";
            case "WINTER" -> "❄️";
            default       -> "🌍";
        };
    }
}