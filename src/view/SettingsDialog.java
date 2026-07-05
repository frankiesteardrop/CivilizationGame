package view;

import controller.MainController;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * پنجره تنظیمات اختصاصی بازی (لایه View).
 * جایگزین مدرن و چشم‌نواز برای JOptionPane های پیش‌فرض سیستم.
 * بر اساس الگوی Facade، تغییرات صدا مستقیماً به MainController منتقل می‌شود.
 */
public class SettingsDialog extends JDialog {

    private final JSlider volumeSlider;
    private final JLabel percentageLabel;

    public SettingsDialog(JFrame parentFrame) {
        super(parentFrame, "Audio Settings", true);

        // حذف حاشیه‌های پیش‌فرض سیستم برای یک دیزاین مدرن و بازی‌محور
        setUndecorated(true);
        setSize(420, 260);
        setLocationRelativeTo(parentFrame);
        setLayout(new BorderLayout());

        // پنل اصلی با پس‌زمینه تیره و حاشیه آبی لاجوردی شیک
        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(new Color(30, 33, 40));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 128, 185), 2),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        // عنوان پنجره
        JLabel titleLabel = new JLabel("🎵 AUDIO SETTINGS", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(236, 240, 241));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        // پنل مرکزی شامل اسلایدر و درصد زنده
        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 10, 5));
        centerPanel.setOpaque(false);

        int currentVolume = MainController.getMusicVolume();
        percentageLabel = new JLabel("Music Volume: " + currentVolume + "%", SwingConstants.CENTER);
        percentageLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        percentageLabel.setForeground(new Color(46, 204, 113));

        volumeSlider = new JSlider(0, 100, currentVolume);
        volumeSlider.setOpaque(false);
        volumeSlider.setForeground(Color.WHITE);
        volumeSlider.setMajorTickSpacing(25);
        volumeSlider.setMinorTickSpacing(5);
        volumeSlider.setPaintTicks(true);
        volumeSlider.setFocusable(false);
        volumeSlider.setCursor(new Cursor(Cursor.HAND_CURSOR));

        // ارسال تغییرات زنده به Facade و آپدیت متن لیبل
        volumeSlider.addChangeListener(e -> {
            int val = volumeSlider.getValue();
            percentageLabel.setText("Music Volume: " + val + "%");
            MainController.setMusicVolume(val);
        });

        centerPanel.add(percentageLabel);
        centerPanel.add(volumeSlider);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

        // دکمه بستن حرفه‌ای با افکت Hover
        JButton closeButton = buildCloseButton();
        mainPanel.add(closeButton, BorderLayout.SOUTH);

        add(mainPanel);
    }

    private JButton buildCloseButton() {
        JButton btn = new JButton("CLOSE & APPLY");
        btn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        btn.setBackground(new Color(41, 128, 185));
        btn.setForeground(Color.WHITE);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        btn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                btn.setBackground(new Color(52, 152, 219));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                btn.setBackground(new Color(41, 128, 185));
            }
        });

        btn.addActionListener(e -> dispose());
        return btn;
    }
}