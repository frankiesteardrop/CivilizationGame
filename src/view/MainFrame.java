package view;

import controller.AudioManager;
import controller.MainController;
import model.GameEventDispatcher;
import model.GameMap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {

    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    private MainController mainController;
    private GamePanel gamePanel;
    private JPanel gameWrapper;

    public MainFrame() {
        setTitle("Civilization VI");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(1024, 700));

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        AudioManager.playMusic("/music.wav");

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

    public void startGame() {

        GameEventDispatcher.clearAllListeners();

        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
        }

        GameMap freshGameMap = new GameMap(5);
        this.mainController = new MainController(freshGameMap);

        this.gamePanel = new GamePanel(mainController);

        gameWrapper = new JPanel(new BorderLayout());
        HUDPanel hudPanel = new HUDPanel(mainController, gamePanel);

        gameWrapper.add(hudPanel, BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
        cardLayout.show(mainContainer, "GAME_UI");

        gamePanel.requestFocusInWindow();
    }


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
            AudioManager.stopMusic();
            System.exit(0);
        }
    }
}