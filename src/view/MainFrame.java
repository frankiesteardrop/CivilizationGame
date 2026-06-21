package view;

import model.GameMap;
import javax.swing.*;

public class MainFrame extends JFrame {
    public MainFrame(GameMap gameMap) {
        setTitle("Civilization - Advanced Programming Project");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1024, 768);
        setLocationRelativeTo(null); // باز شدن پنجره در مرکز صفحه

        GamePanel gamePanel = new GamePanel(gameMap);
        add(gamePanel);
    }
}