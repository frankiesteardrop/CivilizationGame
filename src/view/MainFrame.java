package view;

import com.google.gson.Gson;
import controller.MainController;
import controller.AudioController;
import controller.LobbyController;
import controller.SaveLoadController;
import model.GameEventDispatcher;
import model.GameMap;
import network.client.ClientMessageDispatcher;
import network.client.NetworkManager;
import network.messages.lobby.JoinLobbyRequest;
import network.server.GameServer;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {

    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    private MainController mainController;
    private GamePanel gamePanel;
    private HUDPanel  hudPanel;
    private JPanel    gameWrapper;

    private final AudioController audioController;
    private final Gson gson = new Gson();

    public MainFrame() {
        setTitle("Civilization VI");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(1024, 700));

        cardLayout     = new CardLayout();
        mainContainer  = new JPanel(cardLayout);
        audioController = new AudioController();
        audioController.playMusic("/music.wav");

        mainContainer.add(new MainMenuPanel(this), "MENU");
        add(mainContainer);

        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) { exitGameSafely(); }
        });
    }

    public AudioController getAudioController() { return audioController; }

    // ─── Single-Player ────────────────────────────────────────────────────────

    public void startGame() {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();
        GameMap freshGameMap = new GameMap(20);
        mainController = new MainController(freshGameMap);
        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
    }

    // ─── Multiplayer: Host ────────────────────────────────────────────────────

    /**
     * Starts the game server on localhost and immediately connects as the host.
     * The server runs on a daemon thread so it shuts down when the JVM exits.
     *
     * @param username the display name for this player in the lobby
     */
    public void startServerMode(String username) {
        // Launch GameServer in a background daemon thread
        GameServer gameServer = new GameServer();
        Thread serverThread = new Thread(gameServer::start, "game-server");
        serverThread.setDaemon(true);
        serverThread.start();

        // Give the server a moment to bind the port
        try { Thread.sleep(400); } catch (InterruptedException ignored) {}

        // Connect as a client to localhost
        connectToServer("localhost", username);
    }

    // ─── Multiplayer: Join ────────────────────────────────────────────────────

    /**
     * Connects to an existing game server at the given IP.
     *
     * @param username the display name for this player in the lobby
     * @param serverIp the IP address (or hostname) of the game server
     */
    public void joinServerMode(String username, String serverIp) {
        connectToServer(serverIp, username);
    }

    // ─── Shared Network Connection ────────────────────────────────────────────

    private void connectToServer(String serverIp, String username) {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();

        NetworkManager networkManager = new NetworkManager();
        networkManager.setMyClientId(username); // used by UDP heartbeat

        // Build the lobby controller + view first
        LobbyController lobbyController = new LobbyController(networkManager);
        lobbyController.setMyUsername(username);

        LobbyPanel lobbyPanel = new LobbyPanel(lobbyController);
        JPanel lobbyWrapper = new JPanel(new BorderLayout());
        lobbyWrapper.add(lobbyPanel, BorderLayout.CENTER);
        mainContainer.add(lobbyWrapper, "LOBBY");
        cardLayout.show(mainContainer, "LOBBY");

        // Build the message dispatcher and wire callbacks
        ClientMessageDispatcher dispatcher = new ClientMessageDispatcher(lobbyController);

        // When server broadcasts GAME_START_BROADCAST → switch to game UI
        dispatcher.setOnGameStarted(() -> startMultiplayerMode(networkManager));

        // When server sends GAME_STATE_UPDATE → placeholder for future render update
        dispatcher.setOnGameStateUpdate(update -> {
            // TODO: deserialize filteredMapJson and update local render map
            // For now the notification is enough to see turn changes
            System.out.println("[Client] Game state updated — turn: " + update.getCurrentTurn()
                    + " | active: " + update.getActivePlayerId());
            if (hudPanel != null) {
                // Enable end-turn button if it's our turn
                // (we detect by comparing activePlayerId to networkManager's clientId)
                // For a complete implementation this needs the client's own ID from the server
                hudPanel.onOurTurnStarted();
            }
        });

        dispatcher.setOnDisconnected(() -> {
            JOptionPane.showMessageDialog(this,
                    "Disconnected from the server.",
                    "Connection Lost", JOptionPane.WARNING_MESSAGE);
            returnToMainMenu();
        });

        networkManager.setMessageHandler(dispatcher);

        // Connect TCP (and start UDP heartbeat)
        networkManager.connect(serverIp, 8080);

        // Send JOIN_LOBBY to introduce ourselves
        networkManager.sendRequest(gson.toJson(new JoinLobbyRequest(username)));
    }

    // ─── Multiplayer Game View ────────────────────────────────────────────────

    /**
     * Called by {@link ClientMessageDispatcher} when GAME_START_BROADCAST arrives.
     * Switches from LobbyPanel to the main GamePanel in multiplayer mode.
     */
    public void startMultiplayerMode(NetworkManager networkManager) {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();

        GameMap renderMap = new GameMap(20);
        mainController = new MainController(renderMap);
        mainController.setNetworkManager(networkManager);

        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
        System.out.println("[MainFrame] Multiplayer game view started.");
    }

    /**
     * Re-enables the End Turn button on the HUD.
     * Called from the GAME_STATE_UPDATE handler when this client's turn begins.
     */
    public void notifyOurTurnStarted() {
        if (hudPanel != null) hudPanel.onOurTurnStarted();
    }

    // ─── Shared View Builder ──────────────────────────────────────────────────

    private void buildGameView() {
        gamePanel = new GamePanel(mainController);
        hudPanel  = new HUDPanel(mainController, gamePanel);
        gameWrapper = new JPanel(new BorderLayout());
        gameWrapper.add(hudPanel,  BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);
        mainContainer.add(gameWrapper, "GAME_UI");
    }

    private void cleanUpGameView() {
        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
            gamePanel   = null;
            hudPanel    = null;
        }
    }

    // ─── Load Game ────────────────────────────────────────────────────────────

    public void loadGameFromMenu(String slot) {
        GameMap loadedMap = SaveLoadController.loadGameMap(slot);
        if (loadedMap == null) {
            JOptionPane.showMessageDialog(this, "Save file not found or corrupted!",
                    "Load Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();
        mainController = new MainController(loadedMap);
        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    public void returnToMainMenu() {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();
        cardLayout.show(mainContainer, "MENU");
    }

    public void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Exit Confirmation", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            audioController.stopMusic();
            System.exit(0);
        }
    }
}