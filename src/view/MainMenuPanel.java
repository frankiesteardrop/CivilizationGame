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
        gbc.insets = new Insets(15, 15, 15, 15);
        gbc.gridx = 0;
        gbc.fill = GridBagConstraints.HORIZONTAL;

        JLabel titleLabel = new JLabel("Civilization Sharif", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Arial", Font.BOLD, 40));
        titleLabel.setForeground(Color.LIGHT_GRAY);
        gbc.gridy = 0;
        add(titleLabel, gbc);

        JButton startButton = new JButton("Start Game");
        startButton.setFont(new Font("Arial", Font.BOLD, 20));
        startButton.setFocusPainted(false);
        startButton.addActionListener(e -> mainFrame.startGame());
        gbc.gridy = 1;
        add(startButton, gbc);

        // I1: جایگزینی JOptionPane ساده با LoadGameDialog حرفه‌ای
        JButton loadButton = new JButton("Load Game");
        loadButton.setFont(new Font("Arial", Font.BOLD, 20));
        loadButton.setFocusPainted(false);
        loadButton.addActionListener(e -> openLoadGameDialog());
        gbc.gridy = 2;
        add(loadButton, gbc);

        JButton settingsButton = new JButton("Settings");
        settingsButton.setFont(new Font("Arial", Font.BOLD, 20));
        settingsButton.setFocusPainted(false);
        settingsButton.addActionListener(e -> openSettings());
        gbc.gridy = 3;
        add(settingsButton, gbc);

        JButton exitButton = new JButton("Exit");
        exitButton.setFont(new Font("Arial", Font.BOLD, 20));
        exitButton.setFocusPainted(false);
        exitButton.addActionListener(e -> mainFrame.exitGameSafely());
        gbc.gridy = 4;
        add(exitButton, gbc);
    }

    /**
     * I1: باز کردن LoadGameDialog با نمایش کامل metadata هر Slot.
     *
     * قبلاً از JOptionPane.showInputDialog استفاده می‌شد که فقط اسم Slot را نشان می‌داد.
     * حالا LoadGameDialog اطلاعات کامل (Turn، Season، TH Level، زمان ذخیره) را نمایش می‌دهد.
     *
     * اگر تمام Slot‌ها خالی باشند، یک پیام راهنما نشان داده می‌شود.
     */
    private void openLoadGameDialog() {
        // بررسی سریع: آیا حداقل یک Slot غیرخالی وجود دارد؟
        boolean hasAnySave = false;
        for (String slot : new String[]{"autosave", "slot1", "slot2", "slot3"}) {
            SaveLoadController.SaveMetadata meta = SaveLoadController.readSlotMetadata(slot);
            if (meta != null && !meta.isEmpty) {
                hasAnySave = true;
                break;
            }
        }

        if (!hasAnySave) {
            // هیچ فایل ذخیره‌ای وجود ندارد
            JOptionPane.showMessageDialog(
                    mainFrame,
                    "<html><center><b style='font-size:14px;'>No Save Files Found</b><br/><br/>"
                            + "<span style='color:#888888;'>Start a new game to create save files.<br/>"
                            + "Use the Pause Menu (Esc) during the game to save.</span></center></html>",
                    "No Saves Available",
                    JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }

        // I1: نمایش dialog با metadata کامل
        LoadGameDialog dialog = new LoadGameDialog(mainFrame);
        dialog.setVisible(true); // modal — اینجا block می‌کند تا dialog بسته شود

        String chosenSlot = dialog.getSelectedSlot();
        if (chosenSlot != null && !chosenSlot.isBlank()) {
            mainFrame.loadGameFromMenu(chosenSlot);
        }
    }

    private void openSettings() {
        SettingsDialog settingsDialog = new SettingsDialog(mainFrame);
        settingsDialog.setVisible(true);
    }
}