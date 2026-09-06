package view;

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
    private static final Color ACCENT_GOLD    = new Color(220, 185, 45);
    private static final Color ACCENT_AUTOSAVE= new Color(60, 190, 100);
    private static final Color BORDER_NORMAL  = new Color(42, 48, 62);
    private static final Color BORDER_SEL     = new Color(52, 130, 215);
    private static final Color BORDER_AUTO    = new Color(35, 90, 52);
    private static final Color TEXT_DIM       = new Color(120, 130, 148);
    private static final Color BTN_LOAD       = new Color(35, 100, 175);
    private static final Color BTN_CANCEL     = new Color(45, 50, 65);

    private static final String[] SLOT_KEYS   = {"autosave", "slot1", "slot2", "slot3"};
    private static final String[] SLOT_LABELS = {"🔄  Autosave (Server)", "📁  Server Slot 1", "📁  Server Slot 2", "📁  Server Slot 3"};

    private String        selectedSlot    = null;
    private JPanel        selectedCard    = null;
    private String        selectedKey     = null;
    private final JButton loadBtn;

    public LoadGameDialog(Frame parent) {
        super(parent, "Load Server Game", true);
        setUndecorated(true);
        setSize(540, 580);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_GOLD, 2));

        root.add(buildHeader(), BorderLayout.NORTH);

        JPanel slotListPanel = new JPanel();
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
        scroll.getViewport().setBackground(BG_DARK);
        root.add(scroll, BorderLayout.CENTER);

        loadBtn = new JButton("📂   Load Selected");
        loadBtn.setEnabled(false);
        JButton cancelBtn = new JButton("✕   Cancel");
        root.add(buildFooter(loadBtn, cancelBtn), BorderLayout.SOUTH);

        setContentPane(root);

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

        JLabel title = new JLabel("📂  LOAD SERVER GAME");
        title.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 22));
        title.setForeground(ACCENT_GOLD);
        panel.add(title, BorderLayout.WEST);

        JLabel hint = new JLabel("Select a remote slot");
        hint.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 11));
        hint.setForeground(TEXT_DIM);
        panel.add(hint, BorderLayout.EAST);

        JPanel sep = new JPanel();
        sep.setBackground(new Color(ACCENT_GOLD.getRed(), ACCENT_GOLD.getGreen(), ACCENT_GOLD.getBlue(), 80));
        sep.setPreferredSize(new Dimension(0, 1));
        panel.add(sep, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildSlotCard(String slotKey, String slotLabel, boolean isAutosave) {
        Color baseBg     = isAutosave ? BG_AUTOSAVE : BG_CARD;
        Color hoverBg    = isAutosave ? BG_AUTO_HOVER : BG_CARD_HOVER;
        Color selBg      = isAutosave ? BG_AUTO_SEL : BG_CARD_SEL;
        Color baseBorder = isAutosave ? BORDER_AUTO : BORDER_NORMAL;
        Color titleColor = isAutosave ? ACCENT_AUTOSAVE : ACCENT_GOLD;

        JPanel card = new JPanel(new BorderLayout(10, 4));
        card.setBackground(baseBg);
        card.setOpaque(true);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(baseBorder, 1),
                new EmptyBorder(20, 16, 20, 16)
        ));

        JLabel titleLbl = new JLabel(slotLabel);
        titleLbl.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
        titleLbl.setForeground(titleColor);
        card.add(titleLbl, BorderLayout.CENTER);

        card.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                selectCard(card, slotKey, baseBg, selBg, baseBorder, isAutosave);
                if (e.getClickCount() == 2) {
                    loadSlot(slotKey);
                }
            }
            @Override
            public void mouseEntered(MouseEvent e) {
                if (selectedCard != card) card.setBackground(hoverBg);
            }
            @Override
            public void mouseExited(MouseEvent e) {
                if (selectedCard != card) card.setBackground(baseBg);
            }
        });

        return card;
    }

    private void selectCard(JPanel card, String slotKey,
                            Color baseBg, Color selBg, Color baseBorder, boolean isAutosave) {

        if (selectedCard != null && selectedCard != card) {
            selectedCard.setBackground(isAutosave ? BG_AUTOSAVE : BG_CARD);
            selectedCard.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(isAutosave ? BORDER_AUTO : BORDER_NORMAL, 1),
                    new EmptyBorder(20, 16, 20, 16)
            ));
        }

        selectedCard = card;
        selectedKey  = slotKey;

        card.setBackground(selBg);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_SEL, 2),
                new EmptyBorder(19, 15, 19, 15)
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
        loadBtn.setForeground(Color.WHITE);
        loadBtn.setFocusPainted(false);
        loadBtn.setBorder(new EmptyBorder(10, 24, 10, 24));
        loadBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        loadBtn.addActionListener(e -> {
            if (selectedKey != null) loadSlot(selectedKey);
        });

        cancelBtn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        cancelBtn.setBackground(BTN_CANCEL);
        cancelBtn.setForeground(TEXT_DIM);
        cancelBtn.setFocusPainted(false);
        cancelBtn.setBorder(new EmptyBorder(10, 20, 10, 20));
        cancelBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        cancelBtn.addActionListener(e -> dispose());

        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        btnRow.setOpaque(false);
        btnRow.add(cancelBtn);
        btnRow.add(loadBtn);
        panel.add(btnRow, BorderLayout.CENTER);

        return panel;
    }
}