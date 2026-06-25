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
    private GameMap gameMap; // نگهداری رفرنس نقشه جهت استفاده امن در HUD

    public MainFrame(GameMap gameMap) {
        this.gameMap = gameMap;
        setTitle("Civilization - Advanced Programming Project");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null);

        cardLayout = new CardLayout();
        mainContainer = new JPanel(cardLayout);

        // ساخت پنل منو و پنل بازی
        MainMenuPanel mainMenuPanel = new MainMenuPanel(this);
        gamePanel = new GamePanel(gameMap);

        // افزودن منوی اصلی به کانتینر
        mainContainer.add(mainMenuPanel, "MENU");

        add(mainContainer);

        // مدیریت بستن ایمن پنجره
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exitGameSafely();
            }
        });
    }

    // تغییرات گام ۶: اتصال پنل HUD به صفحه بازی هنگام شروع
    public void startGame() {
        JPanel gameWrapper = new JPanel(new BorderLayout());
        HUDPanel hudPanel = new HUDPanel(this.gameMap, gamePanel);

        gameWrapper.add(hudPanel, BorderLayout.NORTH);
        gameWrapper.add(gamePanel, BorderLayout.CENTER);

        mainContainer.add(gameWrapper, "GAME_UI");
        cardLayout.show(mainContainer, "GAME_UI");

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