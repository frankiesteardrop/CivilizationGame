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

    /**
     * شروع یک بازی جدید.
     */
    public void startGame() {
        // مرحله ۱: پاکسازی کامل listener‌های قدیمی
        GameEventDispatcher.clearAllListeners();

        // مرحله ۲: پاک کردن پنل بازی قبلی از container
        if (gameWrapper != null) {
            mainContainer.remove(gameWrapper);
            gameWrapper = null;
        }

        // مرحله ۳: ساخت GameMap و MainController جدید (بازی تازه)
        GameMap freshGameMap = new GameMap(5);
        this.mainController = new MainController(freshGameMap);

        // مرحله ۴: ساخت GamePanel جدید روی controller جدید
        this.gamePanel = new GamePanel(mainController);

        // مرحله ۵: چیدمان UI بازی
        gameWrapper = new JPanel(new BorderLayout());
        HUDPanel hudPanel = new HUDPanel(mainController, gamePanel);

        gameWrapper.add(hudPanel, BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
        cardLayout.show(mainContainer, "GAME_UI");

        // فوکوس به GamePanel برای دریافت keyboard events
        gamePanel.requestFocusInWindow();
    }

    /**
     * بازگشت به منوی اصلی بدون خروج از برنامه.
     */
    public void returnToMainMenu() {
        GameEventDispatcher.clearAllListeners();
        cardLayout.show(mainContainer, "MENU");
    }

    /**
     * خروج ایمن از بازی با Pop-up تأییدیه.
     * اصلاح گام ۴: تبدیل به public برای استفاده در منوی اصلی (DRY Principle)
     */
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