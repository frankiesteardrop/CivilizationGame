package view;

import com.google.gson.Gson;
import controller.MainController;
import controller.AudioController;
import controller.LobbyController;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class MainFrame extends JFrame {

    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    private MainController mainController;
    private GamePanel gamePanel;
    private HUDPanel  hudPanel;
    private JPanel    gameWrapper;

    private final AudioController audioController;
    private final Gson gson = new Gson();

    private GameServer localServerInstance = null;

    public MainFrame() {
        setTitle("Civilization Sharif");
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

    public void startGame() {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();
        GameMap freshGameMap = new GameMap(20);
        mainController = new MainController(freshGameMap);
        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
    }

    public void startServerMode(String username, String password) {
        localServerInstance = new GameServer();
        CountDownLatch serverReadySignal = new CountDownLatch(1);

        Thread serverThread = new Thread(() -> localServerInstance.start(serverReadySignal), "game-server");
        serverThread.setDaemon(true);
        serverThread.start();

        try {
            boolean ready = serverReadySignal.await(5, TimeUnit.SECONDS);
            if (!ready) {
                JOptionPane.showMessageDialog(this, "Failed to start local server. Port might be in use.", "Server Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }

        connectToServer("localhost", username, password);
    }

    public void joinServerMode(String username, String password, String serverIp) {
        connectToServer(serverIp, username, password);
    }

    private void connectToServer(String serverIp, String username, String password) {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();

        NetworkManager networkManager = new NetworkManager();

        LobbyController lobbyController = new LobbyController(networkManager);
        lobbyController.setMyUsername(username);

        LobbyPanel lobbyPanel = new LobbyPanel(lobbyController);
        JPanel lobbyWrapper = new JPanel(new BorderLayout());
        lobbyWrapper.add(lobbyPanel, BorderLayout.CENTER);
        mainContainer.add(lobbyWrapper, "LOBBY");
        cardLayout.show(mainContainer, "LOBBY");

        ClientMessageDispatcher dispatcher = new ClientMessageDispatcher(lobbyController);

        dispatcher.setOnGameStarted(() -> startMultiplayerMode(networkManager, dispatcher));
        dispatcher.setOnDisconnected(() -> {
            JOptionPane.showMessageDialog(this,
                    "Disconnected from the server.",
                    "Connection Lost", JOptionPane.WARNING_MESSAGE);
            returnToMainMenu();
        });

        networkManager.setMessageHandler(dispatcher);
        networkManager.connect(serverIp, 8080);
        networkManager.sendRequest(gson.toJson(new JoinLobbyRequest(username, password)));
    }

    public void startMultiplayerMode(NetworkManager networkManager,
                                     ClientMessageDispatcher dispatcher) {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();

        GameMap renderMap = GameMap.createClientStub(20);

        mainController = new MainController(renderMap);
        mainController.setNetworkManager(networkManager);

        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();

        if (dispatcher != null && hudPanel != null) {
            dispatcher.setHudPanel(hudPanel);
            dispatcher.setMainController(mainController);
            dispatcher.setGamePanel(gamePanel);
        }

        System.out.println("[MainFrame] Multiplayer game view started.");
    }

    public void notifyOurTurnStarted() {
        if (hudPanel != null) hudPanel.onOurTurnStarted();
    }

    private void buildGameView() {
        gamePanel = new GamePanel(mainController);
        hudPanel  = new HUDPanel(mainController, gamePanel);
        gameWrapper = new JPanel(new BorderLayout());
        gameWrapper.add(hudPanel,  BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);
        mainContainer.add(gameWrapper, "GAME_UI");
    }

    private void cleanUpGameView() {
        if (gamePanel != null) {
            gamePanel.cleanup();
        }
        if (hudPanel != null) {
            hudPanel.cleanup();
        }
        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
            gamePanel   = null;
            hudPanel    = null;
        }
    }

    public void loadGameFromMenu(String slot) {
        MultiplayerSetupDialog dialog = new MultiplayerSetupDialog(this);
        dialog.setVisible(true);

        String username = dialog.getUsername();
        String password = dialog.getPassword();
        boolean isHost = dialog.isHostMode();

        if (username == null) return;

        if (isHost) {
            localServerInstance = new GameServer();
            CountDownLatch serverReadySignal = new CountDownLatch(1);
            Thread serverThread = new Thread(() -> localServerInstance.start(serverReadySignal), "game-server");
            serverThread.setDaemon(true);
            serverThread.start();
            try {
                serverReadySignal.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
            connectToLoadGame("localhost", username, password, slot);
        } else {
            connectToLoadGame(dialog.getServerIp(), username, password, slot);
        }
    }

    private void connectToLoadGame(String serverIp, String username, String password, String slot) {
        GameEventDispatcher.clearAllListeners();
        cleanUpGameView();

        NetworkManager networkManager = new NetworkManager();
        LobbyController lobbyController = new LobbyController(networkManager);
        lobbyController.setMyUsername(username);

        LobbyPanel lobbyPanel = new LobbyPanel(lobbyController);
        JPanel lobbyWrapper = new JPanel(new BorderLayout());
        lobbyWrapper.add(lobbyPanel, BorderLayout.CENTER);
        mainContainer.add(lobbyWrapper, "LOBBY");
        cardLayout.show(mainContainer, "LOBBY");

        ClientMessageDispatcher dispatcher = new ClientMessageDispatcher(lobbyController);
        dispatcher.setOnGameStarted(() -> startMultiplayerMode(networkManager, dispatcher));
        dispatcher.setOnDisconnected(() -> {
            JOptionPane.showMessageDialog(this, "Disconnected from the server.", "Connection Lost", JOptionPane.WARNING_MESSAGE);
            returnToMainMenu();
        });

        dispatcher.setOnMyPlayerIdReceived(myId -> {
            String loadReq = String.format("{\"type\":\"LOAD_GAME\", \"slot\":\"%s\"}", slot);
            networkManager.sendRequest(loadReq);
        });

        networkManager.setMessageHandler(dispatcher);
        networkManager.connect(serverIp, 8080);
        networkManager.sendRequest(gson.toJson(new JoinLobbyRequest(username, password)));
    }

    public void returnToMainMenu() {
        GameEventDispatcher.clearAllListeners();

        cleanUpGameView();

        if (mainController != null && mainController.getNetworkManager() != null) {
            mainController.getNetworkManager().disconnect();
        }

        if (localServerInstance != null) {
            localServerInstance.stop();
            localServerInstance = null;
        }

        cardLayout.show(mainContainer, "MENU");
    }

    public void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit?",
                "Exit Confirmation", JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            audioController.stopMusic();
            if (localServerInstance != null) localServerInstance.stop();
            System.exit(0);
        }
    }
}