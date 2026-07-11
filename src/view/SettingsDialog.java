package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class SettingsDialog extends JDialog {

    private final JSlider volumeSlider;
    private final JLabel percentageLabel;

    public SettingsDialog(MainFrame parentFrame) {
        super(parentFrame, "Audio Settings", true);

        setUndecorated(true);
        setSize(420, 260);
        setLocationRelativeTo(parentFrame);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout(15, 15));
        mainPanel.setBackground(new Color(30, 33, 40));
        mainPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(41, 128, 185), 2),
                BorderFactory.createEmptyBorder(20, 25, 20, 25)
        ));

        JLabel titleLabel = new JLabel("🎵 AUDIO SETTINGS", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 20));
        titleLabel.setForeground(new Color(236, 240, 241));
        mainPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel centerPanel = new JPanel(new GridLayout(2, 1, 10, 5));
        centerPanel.setOpaque(false);

        int currentVolume = parentFrame.getAudioController().getCurrentVolume();
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


        volumeSlider.addChangeListener(e -> {
            int val = volumeSlider.getValue();
            percentageLabel.setText("Music Volume: " + val + "%");
            parentFrame.getAudioController().setVolume(val);
        });

        centerPanel.add(percentageLabel);
        centerPanel.add(volumeSlider);
        mainPanel.add(centerPanel, BorderLayout.CENTER);

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