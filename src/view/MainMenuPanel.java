package view;

import controller.SaveLoadController;

import javax.swing.*;
import java.awt.*;

public class MainMenuPanel extends JPanel {
    private final MainFrame mainFrame;

    public MainMenuPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        setBackground(new Color(30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(12, 15, 12, 15);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Civilization Sharif", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Georgia", Font.BOLD, 40));
        titleLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 0;
        add(titleLabel, gbc);

        // ── Single Player ──────────────────────────────────────────────────────
        JButton startButton = buildMenuButton("▶  New Game", new Color(39, 158, 85));
        startButton.addActionListener(e -> mainFrame.startGame());
        gbc.gridy = 1;
        add(startButton, gbc);

        // ── Multiplayer ────────────────────────────────────────────────────────
        JButton hostButton = buildMenuButton("🖥️  Host Multiplayer Game", new Color(52, 110, 195));
        hostButton.setToolTipText("Start a server on this machine and host a multiplayer lobby.");
        hostButton.addActionListener(e -> openMultiplayerDialog(true));
        gbc.gridy = 2;
        add(hostButton, gbc);

        JButton joinButton = buildMenuButton("🌐  Join Multiplayer Game", new Color(52, 130, 175));
        joinButton.setToolTipText("Connect to an existing multiplayer server.");
        joinButton.addActionListener(e -> openMultiplayerDialog(false));
        gbc.gridy = 3;
        add(joinButton, gbc);

        // ── Load / Settings / Exit ─────────────────────────────────────────────
        JButton loadButton = buildMenuButton("📂  Load Game", new Color(60, 65, 80));
        loadButton.addActionListener(e -> openLoadGameDialog());
        gbc.gridy = 4;
        add(loadButton, gbc);

        JButton settingsButton = buildMenuButton("⚙️  Settings", new Color(60, 65, 80));
        settingsButton.addActionListener(e -> new SettingsDialog(mainFrame).setVisible(true));
        gbc.gridy = 5;
        add(settingsButton, gbc);

        JButton exitButton = buildMenuButton("✕  Exit", new Color(100, 40, 40));
        exitButton.addActionListener(e -> mainFrame.exitGameSafely());
        gbc.gridy = 6;
        add(exitButton, gbc);
    }

    // ─── Multiplayer Setup ────────────────────────────────────────────────────

    private void openMultiplayerDialog(boolean preferHost) {
        MultiplayerSetupDialog dialog = new MultiplayerSetupDialog(mainFrame);
        if (preferHost) {
            // Select the Host tab by default
            dialog.setVisible(true);
        } else {
            dialog.setVisible(true);
        }

        String  username = dialog.getUsername();
        String  ip       = dialog.getServerIp();
        boolean isHost   = dialog.isHostMode();

        if (username == null) return; // cancelled

        if (isHost) {
            mainFrame.startServerMode(username);
        } else {
            mainFrame.joinServerMode(username, ip);
        }
    }

    // ─── Load Dialog ──────────────────────────────────────────────────────────

    private void openLoadGameDialog() {
        boolean hasAnySave = false;
        for (String slot : new String[]{"autosave", "slot1", "slot2", "slot3"}) {
            SaveLoadController.SaveMetadata meta = SaveLoadController.readSlotMetadata(slot);
            if (meta != null && !meta.isEmpty) { hasAnySave = true; break; }
        }

        if (!hasAnySave) {
            JOptionPane.showMessageDialog(mainFrame,
                    "<html><center><b>No Save Files Found</b><br/><br/>"
                            + "<span style='color:#888888;'>Start a game and save it first.</span></center></html>",
                    "No Saves", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        LoadGameDialog dialog = new LoadGameDialog(mainFrame);
        dialog.setVisible(true);
        String chosen = dialog.getSelectedSlot();
        if (chosen != null && !chosen.isBlank()) {
            mainFrame.loadGameFromMenu(chosen);
        }
    }

    // ─── UI Helper ────────────────────────────────────────────────────────────

    private JButton buildMenuButton(String text, Color bg) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 18));
        btn.setBackground(bg);
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(12, 24, 12, 24));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) { btn.setBackground(bg.brighter()); }
            @Override public void mouseExited(java.awt.event.MouseEvent e)  { btn.setBackground(bg); }
        });
        return btn;
    }
}