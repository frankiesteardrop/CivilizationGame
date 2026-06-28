package controller;

import model.GameMap;
import view.MainFrame;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // تولید نقشه منطقی با شعاع 5 واحد
        GameMap gameMap = new GameMap(5);

        // اجرای بخش گرافیک در بستر امن جاوا سویینگ
        SwingUtilities.invokeLater(() -> {
            MainFrame mainFrame = new MainFrame(gameMap);
            mainFrame.setVisible(true);
        });
    }
}