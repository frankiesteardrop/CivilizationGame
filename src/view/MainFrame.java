package view;

import controller.AudioManager;
import model.GameMap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * کلاس اصلی فریم بازی که مدیریت لایه‌ها (منوی اصلی و محیط بازی) را بر عهده دارد.
 */
public class MainFrame extends JFrame {
    private final CardLayout cardLayout;
    private final JPanel mainContainer;
    private final GamePanel gamePanel;
    private final GameMap gameMap;

    public MainFrame(GameMap gameMap) {
        this.gameMap = gameMap;

        // تنظیمات پایه پنجره
        setTitle("Civilization VI - Sharif");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null); // قرارگیری پنجره دقیقاً در مرکز صفحه
        setResizable(false); // قفل کردن تغییر سایز برای حفظ تناسبات گرافیکی نقشه

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // راه‌اندازی و پخش موسیقی پس‌زمینه در لحظه بالا آمدن پنجره
        // اطمینان حاصل کنید که فایل music.wav در پوشه resources قرار دارد
        AudioManager.playMusic("resources/music.wav");

        // ساخت پنل‌های اصلی برنامه
        MainMenuPanel mainMenuPanel = new MainMenuPanel(this);
        gamePanel = new GamePanel(gameMap);

        // افزودن منوی اصلی به کانتینر
        mainContainer.add(mainMenuPanel, "MENU");
        add(mainContainer);

        // مدیریت رویداد بستن ایمن پنجره
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitGameSafely();
            }
        });
    }

    /**
     * سوئیچ کردن از منوی اصلی به محیط اصلی بازی و لود کردن HUD
     */
    public void startGame() {
        JPanel gameWrapper = new JPanel(new BorderLayout());
        HUDPanel hudPanel = new HUDPanel(this.gameMap, gamePanel);

        gameWrapper.add(hudPanel, BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
        cardLayout.show(mainContainer, "GAME_UI");

        // استفاده از این متد به جای requestFocus برای اطمینان از عملکرد صحیح رویدادها در Swing
        gamePanel.requestFocusInWindow();
    }

    /**
     * خروج ایمن از بازی همراه با توقف پراسس‌های صوتی
     */
    private void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit the game?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION);

        if (confirm == JOptionPane.YES_OPTION) {
            AudioManager.stopMusic(); // آزاد کردن منابع کارت صدا قبل از بسته شدن برنامه
            System.exit(0);
        }
    }
}