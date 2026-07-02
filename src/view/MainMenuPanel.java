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

        JLabel titleLabel = new JLabel("Civilization VI - Sharif", SwingConstants.CENTER);
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
        // [گام حل باگ ۲۳]: دریافت میزان صدا از طریق درگاه Facade
        int savedVolume = MainController.getMusicVolume();
        JSlider volumeSlider = new JSlider(0, 100, savedVolume);

        volumeSlider.setMajorTickSpacing(20);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setPaintLabels(true);

        // [گام حل باگ ۲۳]: ارسال تغییرات صدا به درگاه Facade
        volumeSlider.addChangeListener(e -> MainController.setMusicVolume(volumeSlider.getValue()));

        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.add(new JLabel("Music Volume:", SwingConstants.CENTER), BorderLayout.NORTH);
        panel.add(volumeSlider, BorderLayout.CENTER);

        JOptionPane.showMessageDialog(this, panel, "Settings", JOptionPane.PLAIN_MESSAGE);
    }
}