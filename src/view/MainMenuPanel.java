package view;

import controller.MainController; // [گام حل باگ ۲۳]: استفاده از Facade به جای AudioManager مستقیم
import javax.swing.*;
import java.awt.*;

public class MainMenuPanel extends JPanel {
    private final MainFrame mainFrame;

    public MainMenuPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        setBackground(new Color(30, 30, 30));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Civilization VI", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 40));
        titleLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 0;
        add(titleLabel, gbc);

        JButton startButton = new JButton("Start");
        startButton.setFont(new Font("Arial", Font.BOLD, 20));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> mainFrame.startGame());
        gbc.gridy = 1;
        add(startButton, gbc);

        JButton settingsButton = new JButton("Settings");
        settingsButton.setFont(new Font("Arial", Font.BOLD, 20));
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> openSettings());
        gbc.gridy = 2;
        add(settingsButton, gbc);

        JButton exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Arial", Font.BOLD, 20));
        exitButton.setFocusPainted(false);
        exitButton.addActionListener(e -> mainFrame.exitGameSafely());
        gbc.gridy = 3;
        add(exitButton, gbc);
    }

    private void openSettings() {
        SettingsDialog settingsDialog = new SettingsDialog(mainFrame);
        settingsDialog.setVisible(true);
    }
}