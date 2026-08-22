package view;

import controller.MainController;
import controller.SaveLoadController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyEvent;

/**
 * منوی Pause — طبق spec فاز دوم:
 * - ذخیره دستی فقط از این منو (با تأیید Overwrite) [I2]
 * - دکمه Save در حین پردازش Turn یا Animation غیرفعال است [I3]
 * - با کلید Escape قابل باز و بسته شدن است [I4]
 * - نمایش اطلاعات کامل هر Slot (Turn، Season، TH Level، زمان) [I1]
 */
public class PauseMenuDialog extends JDialog {

    // ─── رنگ‌بندی (هماهنگ با تم کلی بازی) ──────────────────────────────────
    private static final Color BG_DARK      = new Color(15, 17, 24);
    private static final Color BG_CARD      = new Color(28, 32, 42);
    private static final Color BG_AUTOSAVE  = new Color(18, 32, 22);
    private static final Color ACCENT_GOLD  = new Color(220, 185, 45);
    private static final Color ACCENT_BLUE  = new Color(52, 130, 215);
    private static final Color ACCENT_GREEN = new Color(46, 180, 80);
    private static final Color ACCENT_RED   = new Color(200, 45, 45);
    private static final Color BTN_RESUME   = new Color(39, 158, 85);
    private static final Color BTN_MENU     = new Color(45, 62, 80);
    private static final Color BTN_EXIT     = new Color(140, 30, 30);
    private static final Color TEXT_MAIN    = new Color(225, 230, 240);
    private static final Color TEXT_DIM     = new Color(125, 135, 150);
    private static final Color SLOT_BORDER  = new Color(48, 55, 70);

    private static final String[] SLOTS  = {"slot1", "slot2", "slot3"};
    private static final String[] LABELS = {"Slot 1", "Slot 2", "Slot 3"};

    private final MainController mainController;
    private final MainFrame      mainFrame;
    private final boolean        isLocked; // true در هنگام animation یا processing

    public PauseMenuDialog(JFrame parent, MainController mainController, boolean isLocked) {
        super(parent, "Game Paused", true);
        this.mainController = mainController;
        this.mainFrame      = (parent instanceof MainFrame) ? (MainFrame) parent : null;
        this.isLocked       = isLocked;

        setUndecorated(true);
        setSize(720, 590);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        // بستن با Escape
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        buildUI();
    }

    // ─── ساخت UI ─────────────────────────────────────────────────────────────

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

        // خط جداکننده پایین header
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

        // دکمه Resume — برجسته‌ترین المان
        JButton resumeBtn = buildWideButton("▶   RESUME GAME", BTN_RESUME, Color.WHITE, 16);
        resumeBtn.addActionListener(e -> dispose());
        outer.add(resumeBtn, BorderLayout.NORTH);

        // بخش Save/Load
        JPanel savesSection = new JPanel(new BorderLayout(0, 8));
        savesSection.setOpaque(false);

        JLabel slotsLabel = new JLabel("SAVE  /  LOAD", SwingConstants.LEFT);
        slotsLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 10));
        slotsLabel.setForeground(TEXT_DIM);
        slotsLabel.setBorder(new EmptyBorder(12, 2, 4, 0));
        savesSection.add(slotsLabel, BorderLayout.NORTH);

        // I3: هشدار قفل بودن Save هنگام animation/processing
        if (isLocked) {
            JLabel lockWarn = new JLabel("⚠️  Cannot save during unit movement or turn processing.");
            lockWarn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 11));
            lockWarn.setForeground(new Color(230, 125, 30));
            lockWarn.setBorder(new EmptyBorder(0, 2, 4, 0));
            savesSection.add(lockWarn, BorderLayout.SOUTH);
        }

        // ۳ کارت Save Slot کنار هم
        JPanel slotsRow = new JPanel(new GridLayout(1, 3, 10, 0));
        slotsRow.setOpaque(false);
        for (int i = 0; i < SLOTS.length; i++) {
            slotsRow.add(buildSlotCard(SLOTS[i], LABELS[i]));
        }
        savesSection.add(slotsRow, BorderLayout.CENTER);

        // کارت Autosave (read-only info + Load)
        JPanel autosaveCard = buildAutosaveCard();

        JPanel center = new JPanel(new BorderLayout(0, 10));
        center.setOpaque(false);
        center.add(savesSection,   BorderLayout.CENTER);
        center.add(autosaveCard,   BorderLayout.SOUTH);

        outer.add(center, BorderLayout.CENTER);
        return outer;
    }

    /**
     * کارت یک Manual Save Slot با اطلاعات کامل [I1] و تأیید Overwrite [I2].
     */
    private JPanel buildSlotCard(String slotName, String slotLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 6));
        card.setBackground(BG_CARD);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SLOT_BORDER, 1),
                new EmptyBorder(12, 12, 12, 12)
        ));

        // عنوان Slot
        JLabel title = new JLabel(slotLabel, SwingConstants.CENTER);
        title.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        title.setForeground(ACCENT_GOLD);
        card.add(title, BorderLayout.NORTH);

        // خواندن metadata بدون لود کامل
        SaveLoadController.SaveMetadata meta = SaveLoadController.readSlotMetadata(slotName);

        // نمایش اطلاعات Slot [I1]
        JPanel infoPanel = new JPanel(new GridLayout(0, 1, 0, 2));
        infoPanel.setOpaque(false);
        infoPanel.setBorder(new EmptyBorder(4, 0, 4, 0));

        // در متد buildSlotCard این بخش را جایگزین کنید:
        if (meta != null && !meta.isEmpty) {
            addInfoRow(infoPanel, "⏳", "Turn " + meta.turnNumber);
            addInfoRow(infoPanel, "🌍", meta.season);
            addInfoRow(infoPanel, "🏰", "TH Level " + meta.thLevel);
            addInfoRow(infoPanel, "📊", meta.gameSummary); // اضافه شده برای نمایش summary [M1]
            addInfoRow(infoPanel, "🕐", meta.saveTime);
        } else {
            JLabel emptyLabel = new JLabel("[ EMPTY ]", SwingConstants.CENTER);
            emptyLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.ITALIC, 12));
            emptyLabel.setForeground(TEXT_DIM);
            emptyLabel.setBorder(new EmptyBorder(8, 0, 8, 0));
            infoPanel.add(emptyLabel);
        }
        card.add(infoPanel, BorderLayout.CENTER);

        // دکمه‌ها
        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 4));
        btnPanel.setOpaque(false);
        btnPanel.setBorder(new EmptyBorder(6, 0, 0, 0));

        // ── دکمه Save ─────────────────────────────────────────────────────────
        // I3: غیرفعال در هنگام animation/processing
        boolean canSave = !isLocked;
        JButton saveBtn = buildCardButton("💾  Save", canSave ? ACCENT_BLUE : new Color(45, 52, 65), canSave);
        if (!canSave) {
            saveBtn.setToolTipText("Cannot save while processing — wait or resume and save normally");
        } else {
            final SaveLoadController.SaveMetadata existingMeta = meta;
            final String slot  = slotName;
            final String label = slotLabel;
            saveBtn.addActionListener(e -> {
                // I2: تأیید Overwrite اگر Slot خالی نیست
                if (existingMeta != null && !existingMeta.isEmpty) {
                    String msg = String.format(
                            "<html><center><b>Overwrite %s?</b><br/><br/>"
                                    + "<span style='color:#aaaaaa'>Turn %d &nbsp;|&nbsp; %s &nbsp;|&nbsp; TH Lv %d<br/>%s</span>"
                                    + "<br/><br/><span style='color:#e74c3c'>⚠️ This cannot be undone.</span></center></html>",
                            label, existingMeta.turnNumber, existingMeta.season,
                            existingMeta.thLevel, existingMeta.saveTime
                    );
                    int confirm = JOptionPane.showConfirmDialog(
                            this, msg, "Overwrite Save?",
                            JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                    );
                    if (confirm != JOptionPane.YES_OPTION) return;
                }
                boolean success = mainController.getSaveLoadController().saveGame(slot);
                if (success) dispose(); // بستن پس از Save تا Slot info refresh شود
            });
        }

        // ── دکمه Load ─────────────────────────────────────────────────────────
        boolean canLoad = (meta != null && !meta.isEmpty);
        JButton loadBtn = buildCardButton("📂  Load", canLoad ? ACCENT_GREEN : new Color(45, 52, 65), canLoad);
        if (!canLoad) {
            loadBtn.setToolTipText("Slot is empty — nothing to load");
        } else if (mainFrame != null) {
            final String slot = slotName;
            loadBtn.addActionListener(e -> {
                // I2: هشدار از دست رفتن progress ذخیره‌نشده
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "<html><center><b>Load " + slotLabel + "?</b><br/><br/>"
                                + "<span style='color:#e74c3c'>⚠️ All unsaved progress will be lost!</span><br/>"
                                + "<span style='color:#aaaaaa'>Consider saving first.</span></center></html>",
                        "Load Game?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    dispose();
                    mainFrame.loadGameFromMenu(slot);
                }
            });
        }

        btnPanel.add(saveBtn);
        btnPanel.add(loadBtn);
        card.add(btnPanel, BorderLayout.SOUTH);

        return card;
    }

    /**
     * کارت اطلاعات Autosave (فقط نمایش + Load).
     */
    private JPanel buildAutosaveCard() {
        JPanel card = new JPanel(new BorderLayout(10, 0));
        card.setBackground(BG_AUTOSAVE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(35, 75, 50), 1),
                new EmptyBorder(10, 14, 10, 14)
        ));

        JLabel iconLabel = new JLabel("🔄  Autosave:");
        iconLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 12));
        iconLabel.setForeground(new Color(80, 195, 120));
        card.add(iconLabel, BorderLayout.WEST);

        SaveLoadController.SaveMetadata meta = SaveLoadController.readSlotMetadata("autosave");
        JLabel infoLabel;
        if (meta != null && !meta.isEmpty) {
            infoLabel = new JLabel(String.format(
                    "Turn %d  |  %s  |  TH Lv %d  |  %s",
                    meta.turnNumber, meta.season, meta.thLevel, meta.saveTime
            ));
        } else {
            infoLabel = new JLabel("No autosave available yet.");
        }
        infoLabel.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 12));
        infoLabel.setForeground(TEXT_DIM);
        card.add(infoLabel, BorderLayout.CENTER);

        // دکمه Load Autosave
        if (meta != null && !meta.isEmpty && mainFrame != null) {
            JButton loadBtn = buildCardButton("Load", new Color(38, 85, 55), true);
            loadBtn.addActionListener(e -> {
                int confirm = JOptionPane.showConfirmDialog(
                        this,
                        "<html><center><b>Load Autosave?</b><br/>"
                                + "<span style='color:#e74c3c'>⚠️ Unsaved progress will be lost!</span></center></html>",
                        "Load Autosave?",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE
                );
                if (confirm == JOptionPane.YES_OPTION) {
                    dispose();
                    mainFrame.loadGameFromMenu("autosave");
                }
            });
            card.add(loadBtn, BorderLayout.EAST);
        }

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

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private void addInfoRow(JPanel panel, String icon, String value) {
        JLabel row = new JLabel(icon + "  " + value);
        row.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 11));
        row.setForeground(TEXT_DIM);
        panel.add(row);
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