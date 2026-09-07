package view;

import controller.LobbyController;
import model.maps.MapDefinition;
import model.maps.PreDesignedMaps;
import network.messages.lobby.LobbyPlayer;
import network.messages.lobby.LobbyUpdateBroadcast;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;


public class LobbyPanel extends JPanel {

    private final LobbyController controller;
    private final JPanel      playerListPanel;
    private final JTextArea   chatArea;
    private final JTextField  chatInput;
    private final JButton     readyButton;
    private final JButton     startButton;

    private final JComboBox<String> mapComboBox;
    private final JLabel            mapSelectionLabel;
    private boolean amHost = false;

    public LobbyPanel(LobbyController controller) {
        this.controller = controller;
        this.controller.setView(this);

        setLayout(new BorderLayout(15, 15));
        setBackground(new Color(25, 28, 35));
        setBorder(new EmptyBorder(20, 20, 20, 20));

        playerListPanel = new JPanel();
        playerListPanel.setLayout(new BoxLayout(playerListPanel, BoxLayout.Y_AXIS));
        playerListPanel.setBackground(new Color(35, 39, 48));

        JScrollPane playerScroll = new JScrollPane(playerListPanel);
        playerScroll.setPreferredSize(new Dimension(280, 0));
        playerScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219), 1), "Players"));
        playerScroll.getViewport().setBackground(new Color(35, 39, 48));

        List<MapDefinition> allMaps = PreDesignedMaps.getAllMaps();
        String[] mapNames = allMaps.stream()
                .map(MapDefinition::getDisplayName)
                .toArray(String[]::new);
        String[] mapIds = allMaps.stream()
                .map(MapDefinition::getId)
                .toArray(String[]::new);

        mapSelectionLabel = new JLabel("🗺 Map:");
        mapSelectionLabel.setForeground(new Color(189, 195, 199));
        mapSelectionLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));

        mapComboBox = new JComboBox<>(mapNames);
        mapComboBox.setBackground(new Color(45, 50, 65));
        mapComboBox.setForeground(Color.WHITE);
        mapComboBox.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        mapComboBox.setEnabled(false); // only enabled when we're the host
        mapComboBox.setToolTipText("Only the host can change the map selection.");
        mapComboBox.addActionListener(e -> {
            if (amHost && mapComboBox.getSelectedIndex() >= 0) {
                String selectedId = mapIds[mapComboBox.getSelectedIndex()];
                controller.selectMap(selectedId);
            }
        });

        JPanel mapPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        mapPanel.setBackground(new Color(30, 34, 42));
        mapPanel.setBorder(BorderFactory.createLineBorder(new Color(52, 152, 219), 1));
        mapPanel.add(mapSelectionLabel);
        mapPanel.add(mapComboBox);

        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setBackground(new Color(15, 17, 24));
        chatArea.setForeground(Color.WHITE);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219), 1), "Global Chat"));

        chatInput = new JTextField();
        chatInput.setBackground(new Color(45, 50, 65));
        chatInput.setForeground(Color.WHITE);
        chatInput.setCaretColor(Color.WHITE);
        chatInput.setFont(new Font("Segoe UI", Font.PLAIN, 14));
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

        readyButton = new JButton("✅ Toggle Ready");
        readyButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        readyButton.setBackground(new Color(46, 204, 113));
        readyButton.setForeground(Color.WHITE);
        readyButton.setFocusPainted(false);
        readyButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        readyButton.addActionListener(e -> controller.toggleReady());

        startButton = new JButton("▶ Start Game");
        startButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        startButton.setBackground(new Color(192, 57, 43));
        startButton.setForeground(Color.WHITE);
        startButton.setFocusPainted(false);
        startButton.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
        startButton.setEnabled(false);
        startButton.setToolTipText("Only available to the host when all players are Ready.");
        startButton.addActionListener(e -> controller.requestStartGame());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 4));
        buttonPanel.setBackground(new Color(30, 34, 42));
        buttonPanel.add(readyButton);
        buttonPanel.add(startButton);

        JPanel southPanel = new JPanel(new BorderLayout(0, 5));
        southPanel.setOpaque(false);
        southPanel.add(mapPanel, BorderLayout.NORTH);
        southPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(playerScroll, BorderLayout.WEST);
        add(chatPanel,    BorderLayout.CENTER);
        add(southPanel,   BorderLayout.SOUTH);
    }


    public void updateLobbyState(LobbyUpdateBroadcast update, boolean iAmHost) {
        this.amHost = iAmHost;

        playerListPanel.removeAll();
        boolean allReady = !update.getPlayers().isEmpty();

        for (LobbyPlayer player : update.getPlayers()) {
            String hostBadge  = player.isHost() ? " 👑" : "";
            String readyBadge = player.isReady() ? " [READY]" : " [WAITING]";
            JLabel label = new JLabel(player.getUsername() + hostBadge + readyBadge);
            label.setForeground(player.isReady()
                    ? new Color(46, 204, 113) : new Color(231, 76, 60));
            label.setFont(new Font("Segoe UI", Font.BOLD, 13));
            label.setBorder(new EmptyBorder(4, 8, 4, 8));
            playerListPanel.add(label);

            if (!player.isReady()) allReady = false;
        }
        playerListPanel.revalidate();
        playerListPanel.repaint();

        mapComboBox.setEnabled(iAmHost);
        mapComboBox.setToolTipText(iAmHost
                ? "Select the map for this game session."
                : "Only the host can change the map.");

        String serverMapId = update.getSelectedMapId();
        if (serverMapId != null) {
            List<MapDefinition> allMaps = PreDesignedMaps.getAllMaps();
            for (int i = 0; i < allMaps.size(); i++) {
                if (allMaps.get(i).getId().equals(serverMapId)) {
                    java.awt.event.ActionListener[] listeners = mapComboBox.getActionListeners();
                    for (java.awt.event.ActionListener l : listeners) mapComboBox.removeActionListener(l);
                    mapComboBox.setSelectedIndex(i);
                    for (java.awt.event.ActionListener l : listeners) mapComboBox.addActionListener(l);
                    break;
                }
            }
        }

        startButton.setEnabled(iAmHost && allReady && update.getPlayers().size() >= 2);
        startButton.setVisible(iAmHost);
    }

    public void appendChatMessage(String message) {
        chatArea.append(message);
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
}