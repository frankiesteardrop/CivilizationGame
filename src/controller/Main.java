package controller;

import view.MainFrame;
import javax.swing.SwingUtilities;

/**
 * نقطه ورود اصلی برنامه.
 * اصلاح گام ۴: MainFrame بدون GameMap ساخته می‌شود —
 * GameMap در هر بار startGame() از نو تولید می‌شود.
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame();
            mainFrame.setVisible(true);
        });
    }
}