package view;

import controller.LobbyController;
import network.messages.lobby.LobbyPlayer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

public class LobbyPanel extends JPanel {
    private final LobbyController controller;
    private final JPanel playerListPanel;
    private final JTextArea chatArea;
    private final JTextField chatInput;
    private final JButton readyButton;
    private final JButton startButton;

    public LobbyPanel(LobbyController controller) {
        this.controller = controller;
        this.controller.setView(this);

        setLayout(new BorderLayout(15, 15));
        setBackground(new Color(25, 28, 35));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        // --- پنل لیست بازیکنان ---
        playerListPanel = new JPanel();
        playerListPanel.setLayout(new BoxLayout(playerListPanel, BoxLayout.Y_AXIS));
        playerListPanel.setBackground(new Color(35, 39, 48));

        JScrollPane playerScroll = new JScrollPane(playerListPanel);
        playerScroll.setPreferredSize(new Dimension(300, 0));
        playerScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219)), "Players"));
        playerScroll.getViewport().setBackground(new Color(35, 39, 48));

        // --- پنل چت (Real-time) ---
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(15, 17, 24));
        chatArea.setForeground(Color.WHITE);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219)), "Global Chat"));

        chatInput = new JTextField();
        chatInput.setBackground(new Color(45, 50, 65));
        chatInput.setForeground(Color.WHITE);
        chatInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    controller.sendChatMessage(chatInput.getText());
                    chatInput.setText("");
                }
            }
        });

        JPanel chatPanel = new JPanel(new BorderLayout(5, 5));
        chatPanel.setOpaque(false);
        chatPanel.add(chatScroll, BorderLayout.CENTER);
        chatPanel.add(chatInput, BorderLayout.SOUTH);

        // --- پنل دکمه‌ها ---
        readyButton = new JButton("Toggle Ready");
        readyButton.setBackground(new Color(46, 204, 113));
        readyButton.setForeground(Color.WHITE);
        readyButton.addActionListener(e -> controller.toggleReady());

        startButton = new JButton("Start Game");
        startButton.setBackground(new Color(192, 57, 43));
        startButton.setForeground(Color.WHITE);
        startButton.setEnabled(false); // فقط هاست می‌تواند بازی را شروع کند
        startButton.addActionListener(e -> controller.requestStartGame());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setOpaque(false);
        buttonPanel.add(readyButton);
        buttonPanel.add(startButton);

        add(playerScroll, BorderLayout.WEST);
        add(chatPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    public void updatePlayerList(List<LobbyPlayer> players) {
        playerListPanel.removeAll();
        boolean allReady = true;
        boolean amIHost = false; // در یک پیاده‌سازی کامل، باید بررسی شود شناسه کلاینت با این بازیکن برابر است یا خیر

        for (LobbyPlayer player : players) {
            String status = player.isReady() ? " [READY] " : " [WAITING] ";
            String role = player.isHost() ? "👑 " : "👤 ";
            JLabel pLabel = new JLabel(role + player.getUsername() + status);
            pLabel.setForeground(player.isReady() ? new Color(46, 204, 113) : new Color(231, 76, 60));
            pLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
            playerListPanel.add(pLabel);

            if (!player.isReady()) allReady = false;
        }

        // فقط برای هاست باز می‌شود و اگر همه آماده باشند
        startButton.setEnabled(allReady); // شرط هاست بودن باید دقیقاً با چک کردن ID کلاینت لحاظ شود

        playerListPanel.revalidate();
        playerListPanel.repaint();
    }

    public void appendChatMessage(String message) {
        chatArea.append(message);
        chatArea.setCaretPosition(chatArea.getDocument().getLength()); // اسکرول خودکار به پایین
    }
}