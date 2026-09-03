package view;

import controller.MainController;
import controller.AudioController;
import controller.SaveLoadController;
import model.GameEventDispatcher;
import model.GameMap;
import network.client.NetworkManager;

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
    private JPanel gameWrapper;

    private final AudioController audioController;

    public MainFrame() {
        setTitle("Civilization VI");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(1024, 700));

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        this.audioController = new AudioController();
        this.audioController.playMusic("/music.wav");

        MainMenuPanel mainMenuPanel = new MainMenuPanel(this);
        mainContainer.add(mainMenuPanel, "MENU");

        add(mainContainer);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitGameSafely();
            }
        });
    }

    public AudioController getAudioController() {
        return audioController;
    }

    // ─── Single-Player Mode ───────────────────────────────────────────────────

    public void startGame() {
        GameEventDispatcher.clearAllListeners();

        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
        }

        GameMap freshGameMap = new GameMap(20);
        this.mainController = new MainController(freshGameMap);
        // networkManager stays null → single-player mode

        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
    }

    // ─── Multiplayer Mode — B5 ────────────────────────────────────────────────

    /**
     * Switches the UI from the LobbyPanel to the main game view in multiplayer mode.
     *
     * <p>Called by {@link network.client.ClientMessageDispatcher} when the server
     * broadcasts a {@code GAME_START_BROADCAST} message.
     *
     * <p>The {@link NetworkManager} is set on the {@link MainController} so that
     * all subsequent UI actions (end turn, attacks, builds) are routed to the
     * server instead of executing locally.
     *
     * @param networkManager the active connection to the game server
     */
    public void startMultiplayerMode(NetworkManager networkManager) {
        GameEventDispatcher.clearAllListeners();

        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
        }

        // Create a local GameMap for rendering purposes only.
        // The authoritative state will be overwritten by GAME_STATE_UPDATE messages.
        GameMap renderMap = new GameMap(20);
        this.mainController = new MainController(renderMap);

        // Attach the network manager — this switches all actions to server-mode
        this.mainController.setNetworkManager(networkManager);

        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();

        System.out.println("[MainFrame] Multiplayer game view started.");
    }

    /**
     * Re-enables the End Turn button on the HUD when the server indicates
     * it is now this client's turn. Called from the GAME_STATE_UPDATE handler
     * in {@link network.client.ClientMessageDispatcher}.
     */
    public void notifyOurTurnStarted() {
        if (hudPanel != null) {
            hudPanel.onOurTurnStarted();
        }
    }

    // ─── Shared Game View Builder ─────────────────────────────────────────────

    /** Creates and shows the game panel + HUD. Shared by single-player and multiplayer. */
    private void buildGameView() {
        this.gamePanel = new GamePanel(mainController);
        this.hudPanel  = new HUDPanel(mainController, gamePanel);

        gameWrapper = new JPanel(new BorderLayout());
        gameWrapper.add(hudPanel,   BorderLayout.NORTH);
        gameWrapper.add(gamePanel,  BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
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

        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
        }
        this.mainController = new MainController(loadedMap);
        // networkManager stays null → single-player load

        buildGameView();
        cardLayout.show(mainContainer, "GAME_UI");
        gamePanel.requestFocusInWindow();
    }

    // ─── Navigation ───────────────────────────────────────────────────────────

    public void returnToMainMenu() {
        GameEventDispatcher.clearAllListeners();
        cardLayout.show(mainContainer, "MENU");
    }

    public void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to exit the game?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE
        );
        if (confirm == JOptionPane.YES_OPTION) {
            audioController.stopMusic();
            System.exit(0);
        }
    }
}