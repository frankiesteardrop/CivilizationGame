package view;

import model.GameMap;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class MainFrame extends JFrame {
    private CardLayout cardLayout;
    private JPanel mainContainer;
    private GamePanel gamePanel;

    public MainFrame(GameMap gameMap) {
        setTitle("Civilization - Advanced Programming Project");
        // غیرفعال کردن بسته شدن مستقیم تا پاپ آپ خروج کار کنه
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // ساخت پنل ها
        MainMenuPanel mainMenuPanel = new MainMenuPanel(this);
        gamePanel = new GamePanel(gameMap);

        // اضافه کردن پنل ها به کانتینر اصلی
        mainContainer.add(mainMenuPanel, "MENU");
        mainContainer.add(gamePanel, "GAME");

        add(mainContainer);

        // مدیریت بستن پنجره با دکمه ضربدر بالای صفحه
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitGameSafely();
            }
        });
    }

    // متدی که دکمه Start تو منو صداش میزنه تا مپ بازی باز بشه
    public void startGame() {
        cardLayout.show(mainContainer, "GAME");
        gamePanel.requestFocus();
    }

    private void exitGameSafely() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to exit the game?",
                "Exit Confirmation",
                JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            System.exit(0);
        }
    }
}