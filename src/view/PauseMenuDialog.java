package view;

import controller.MainController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

public class PauseMenuDialog extends JDialog {

    private static final Color BG_DARK      = new Color(15, 17, 24);
    private static final Color BG_CARD      = new Color(28, 32, 42);
    private static final Color BG_AUTOSAVE  = new Color(18, 32, 22);
    private static final Color ACCENT_GOLD  = new Color(220, 185, 45);
    private static final Color ACCENT_BLUE  = new Color(52, 130, 215);
    private static final Color ACCENT_GREEN = new Color(46, 180, 80);
    private static final Color BTN_RESUME   = new Color(39, 158, 85);
    private static final Color BTN_MENU     = new Color(45, 62, 80);
    private static final Color BTN_EXIT     = new Color(140, 30, 30);
    private static final Color TEXT_MAIN    = new Color(225, 230, 240);
    private static final Color TEXT_DIM     = new Color(125, 135, 150);
    private static final Color SLOT_BORDER  = new Color(48, 55, 70);

    private static final String[] SLOTS  = {"slot1", "slot2", "slot3"};
    private static final String[] LABELS = {"Server Slot 1", "Server Slot 2", "Server Slot 3"};

    private final MainController mainController;
    private final MainFrame      mainFrame;
    private final boolean        isLocked;

    public PauseMenuDialog(JFrame parent, MainController mainController, boolean isLocked) {
        super(parent, "Game Paused", true);
        this.mainController = mainController;
        this.mainFrame      = (parent instanceof MainFrame) ? (MainFrame) parent : null;
        this.isLocked       = isLocked;

        setUndecorated(true);
        setSize(720, 520);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        buildUI();
    }

    private void buildUI() {
        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(BG_DARK);
        root.setBorder(BorderFactory.createLineBorder(ACCENT_GOLD, 2));

        root.add(buildHeader(),     BorderLayout.NORTH);
        root.add(buildCenterPanel(), BorderLayout.CENTER);
        root.add(buildFooter(),     BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(20, 23, 32));
        panel.setBorder(new EmptyBorder(18, 24, 18, 24));

        JLabel title = new JLabel("⏸  GAME PAUSED", SwingConstants.CENTER);
        title.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 24));
        title.setForeground(ACCENT_GOLD);
        panel.add(title, BorderLayout.CENTER);

        JPanel sep = new JPanel();
        sep.setBackground(new Color(ACCENT_GOLD.getRed(), ACCENT_GOLD.getGreen(), ACCENT_GOLD.getBlue(), 100));
        sep.setPreferredSize(new Dimension(0, 1));
        panel.add(sep, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildCenterPanel() {
        JPanel outer = new JPanel(new BorderLayout(0, 12));
        outer.setBackground(BG_DARK);
        outer.setBorder(new EmptyBorder(16, 20, 12, 20));

        JButton resumeBtn = buildWideButton("▶   RESUME GAME", BTN_RESUME, Color.WHITE, 16);
        resumeBtn.addActionListener(e -> dispose());
        outer.add(resumeBtn, BorderLayout.NORTH);

        JPanel savesSection = new JPanel(new BorderLayout(0, 8));
        savesSection.setOpaque(false);

        JLabel slotsLabel = new JLabel("SERVER SAVE / LOAD", SwingConstants.LEFT);
        slotsLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 10));
        slotsLabel.setForeground(TEXT_DIM);
        slotsLabel.setBorder(new EmptyBorder(12, 2, 4, 0));
        savesSection.add(slotsLabel, BorderLayout.NORTH);

        if (isLocked) {
            JLabel lockWarn = new JLabel("⚠️ Cannot save during unit movement or turn processing.");
            lockWarn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 11));
            lockWarn.setForeground(new Color(230, 125, 30));
            lockWarn.setBorder(new EmptyBorder(0, 2, 4, 0));
            savesSection.add(lockWarn, BorderLayout.SOUTH);
        }

        JPanel slotsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        slotsRow.setOpaque(false);
        for (int i = 0; i < SLOTS.length; i++) {
            slotsRow.add(buildSlotCard(SLOTS[i], LABELS[i]));
        }
        savesSection.add(slotsRow, BorderLayout.CENTER);

        JPanel autosaveCard = buildAutosaveCard();

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(savesSection,   BorderLayout.CENTER);
        center.add(autosaveCard,   BorderLayout.SOUTH);

        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    private JPanel buildSlotCard(String slotName, String slotLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SLOT_BORDER, 1),
                new EmptyBorder(12, 12, 12, 12)
        ));
        JLabel title = new JLabel(slotLabel, SwingConstants.CENTER);
        title.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        title.setForeground(ACCENT_GOLD);
        card.add(title, BorderLayout.NORTH);

        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        boolean canSave = !isLocked;
        JButton saveBtn = buildCardButton("💾  Save to Server", canSave ? ACCENT_BLUE : new Color(45, 52, 65), canSave);
        if (canSave) {
            saveBtn.addActionListener(e -> {
                mainController.getSaveLoadController().saveGame(slotName);
                dispose();
            });
        }

        JButton loadBtn = buildCardButton("📂  Load from Server", ACCENT_GREEN, true);
        loadBtn.addActionListener(e -> {
            if (mainController.getNetworkManager() != null) {
                mainController.getNetworkManager().sendRequest(String.format("{\"type\":\"LOAD_GAME\", \"slot\":\"%s\"}", slotName));
            }
            dispose();
        });

        btnPanel.add(saveBtn);
        btnPanel.add(loadBtn);
        card.add(btnPanel, BorderLayout.CENTER);

        return card;
    }

    private JPanel buildAutosaveCard() {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(BG_AUTOSAVE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(35, 75, 50), 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel iconLabel = new JLabel("🔄  Server Autosave:");
        iconLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 12));
        iconLabel.setForeground(new Color(80, 195, 120));
        card.add(iconLabel, BorderLayout.WEST);

        JLabel infoLabel = new JLabel("Managed safely on the remote server.");
        infoLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 12));
        infoLabel.setForeground(TEXT_DIM);
        card.add(infoLabel, BorderLayout.CENTER);

        JButton loadBtn = buildCardButton("Load Autosave", new Color(38, 85, 55), true);
        loadBtn.addActionListener(e -> {
            if (mainController.getNetworkManager() != null) {
                mainController.getNetworkManager().sendRequest("{\"type\":\"LOAD_GAME\", \"slot\":\"autosave\"}");
            }
            dispose();
        });
        card.add(loadBtn, BorderLayout.EAST);

        return card;
    }

    private JPanel buildFooter() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 10, 0));
        panel.setBackground(new Color(18, 21, 28));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, SLOT_BORDER),
                new EmptyBorder(14, 20, 14, 20)
        ));

        JButton menuBtn = buildWideButton("🏠   MAIN MENU", BTN_MENU, TEXT_MAIN, 14);
        menuBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "<html><center>Return to Main Menu?<br/>"
                            + "<span style='color:#e74c3c'>⚠️ Unsaved progress will be lost!</span></center></html>",
                    "Main Menu",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
            );
            if (confirm == JOptionPane.YES_OPTION && mainFrame != null) {
                dispose();
                mainFrame.returnToMainMenu();
            }
        });

        JButton exitBtn = buildWideButton("✕   EXIT GAME", BTN_EXIT, Color.WHITE, 14);
        exitBtn.addActionListener(e -> {
            dispose();
            if (mainFrame != null) mainFrame.exitGameSafely();
        });

        panel.add(menuBtn);
        panel.add(exitBtn);
        return panel;
    }

    private JButton buildWideButton(String text, Color bg, Color fg, int fontSize) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, fontSize));
        btn.setBackground(bg);
        btn.setForeground(fg);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(12, 0, 12, 0));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }

    private JButton buildCardButton(String text, Color bg, boolean enabled) {
        JButton btn = new JButton(text);
        btn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 11));
        btn.setBackground(bg);
        btn.setForeground(enabled ? Color.WHITE : TEXT_DIM);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setEnabled(enabled);
        btn.setBorder(new EmptyBorder(6, 8, 6, 8));
        btn.setCursor(enabled ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
        if (enabled) {
            btn.addMouseListener(new java.awt.event.MouseAdapter() {
                @Override public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(bg.brighter()); }
                @Override public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(bg); }
            });
        }
        return btn;
    }
}