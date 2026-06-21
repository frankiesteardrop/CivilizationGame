package view;

import javax.swing.*;
import java.awt.*;

public class MainMenuPanel extends JPanel {
    private MainFrame mainFrame;

    public MainMenuPanel(MainFrame mainFrame) {
        this.mainFrame = mainFrame;
        setLayout(new GridBagLayout());
        setBackground(new Color(30, 30, 30)); // رنگ پس زمینه تاریک و شیک برای منو

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        // عنوان بازی
        JLabel titleLabel = new JLabel("Civilization VI - Sharif", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 40));
        titleLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 0;
        add(titleLabel, gbc);

        // دکمه شروع بازی
        JButton startButton = new JButton("Start");
        startButton.setFont(new Font("Arial", Font.BOLD, 20));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> mainFrame.startGame());
        gbc.gridy = 1;
        add(startButton, gbc);

        // دکمه تنظیمات
        JButton settingsButton = new JButton("Settings");
        settingsButton.setFont(new Font("Arial", Font.BOLD, 20));
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> openSettings());
        gbc.gridy = 2;
        add(settingsButton, gbc);

        // دکمه خروج
        JButton exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Arial", Font.BOLD, 20));
        exitButton.setFocusPainted(false);
        exitButton.addActionListener(e -> exitGame());
        gbc.gridy = 3;
        add(exitButton, gbc);
    }

    private void openSettings() {
        // ایجاد Scroll Bar (اسلایدر) برای تنظیم صدا
        JSlider volumeSlider = new JSlider(0, 100, 50);
        volumeSlider.setMajorTickSpacing(20);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("Music Volume:", SwingConstants.CENTER), BorderLayout.NORTH);
        panel.add(volumeSlider, BorderLayout.CENTER);

        // نمایش پاپ آپ تنظیمات
        JOptionPane.showMessageDialog(this, panel, "Settings", JOptionPane.PLAIN_MESSAGE);
    }

    private void exitGame() {
        // نمایش پاپ آپ تاییدیه خروج
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit the game?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }
}