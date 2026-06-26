package view;

import controller.AudioManager;
import controller.MainController;
import model.GameMap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {
    private final CardLayout cardLayout;
    private final JPanel mainContainer;
    private final GamePanel gamePanel;
    private final MainController mainController; // استفاده از MainController به جای GameController

    public MainFrame(GameMap gameMap) {
        this.mainController = new MainController(gameMap);

        setTitle("Civilization VI - Sharif");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);
        setResizable(false);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        AudioManager.playMusic("resources/music.wav");

        MainMenuPanel mainMenuPanel = new MainMenuPanel(this);
        gamePanel = new GamePanel(mainController);

        mainContainer.add(mainMenuPanel, "MENU");
        add(mainContainer);

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { exitGameSafely(); }
        });
    }

    public void startGame() {
        JPanel gameWrapper = new JPanel(new BorderLayout());
        HUDPanel hudPanel = new HUDPanel(mainController, gamePanel);

        gameWrapper.add(hudPanel, BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
        cardLayout.show(mainContainer, "GAME_UI");

        gamePanel.requestFocusInWindow();
    }

    private void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to exit the game?", "Exit", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            AudioManager.stopMusic();
            System.exit(0);
        }
    }
}