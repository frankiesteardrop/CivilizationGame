package view;

import controller.AudioManager;
import controller.MainController;
import model.GameEventDispatcher;
import model.GameMap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * پنجره اصلی بازی — نقطه ورود UI.
 *
 * اصلاح گام ۴: هر بار که startGame() صدا زده می‌شود،
 * یک GameMap و MainController کاملاً جدید ساخته می‌شود
 * تا بازیکن یک بازی تازه داشته باشد.
 */
public class MainFrame extends JFrame {

    private final CardLayout cardLayout;
    private final JPanel mainContainer;

    // اصلاح گام ۴: این فیلدها دیگر final نیستند — در هر restart جدید ساخته می‌شوند
    private MainController mainController;
    private GamePanel gamePanel;
    private JPanel gameWrapper;

    public MainFrame() {
        setTitle("Civilization VI - Sharif");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1280, 800);
        setLocationRelativeTo(null);
        setResizable(true);
        setMinimumSize(new Dimension(1024, 700));

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // پخش موزیک پس‌زمینه از همان ابتدا
        AudioManager.playMusic("/music.wav");

        // نمایش منوی اصلی
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
     *
     * اصلاح گام ۴: هر بار که این متد صدا زده می‌شود:
     * ۱. تمام listener‌های قدیمی پاکسازی می‌شوند (جلوگیری از Memory Leak)
     * ۲. یک GameMap جدید با نقشه تازه تولید می‌شود
     * ۳. یک MainController جدید روی آن GameMap ساخته می‌شود
     * ۴. UI بازی کاملاً از نو ساخته می‌شود
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
     * برای دکمه‌ای که ممکن است در آینده به بازی اضافه شود.
     */
    public void returnToMainMenu() {
        GameEventDispatcher.clearAllListeners();
        cardLayout.show(mainContainer, "MENU");
    }

    /**
     * خروج ایمن از بازی با Pop-up تأییدیه.
     */
    private void exitGameSafely() {
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