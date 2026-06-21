package view;

import model.GameMap;
import model.Hex;
import model.ResourceType;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GamePanel extends JPanel {
    private GameMap gameMap;
    private double zoomFactor = 1.0;
    private int offsetX = 400; // مرکز صفحه نمایش
    private int offsetY = 300;
    private Point lastMousePosition;
    private final int HEX_SIZE = 40;

    public GamePanel(GameMap gameMap) {
        this.gameMap = gameMap;
        setBackground(Color.BLACK); // پس زمینه تاریک بیرون نقشه

        // پیاده سازی جابجایی دوربین روی نقشه
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                int dx = e.getX() - lastMousePosition.x;
                int dy = e.getY() - lastMousePosition.y;
                offsetX += dx;
                offsetY += dy;
                lastMousePosition = e.getPoint();
                repaint();
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        // پیاده سازی زوم گسسته با چرخ موس
        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) {
                zoomFactor += 0.2; // زوم به داخل
            } else {
                zoomFactor -= 0.2; // زوم به بیرون
            }
            // محدود کردن میزان زوم
            if (zoomFactor < 0.4) zoomFactor = 0.4;
            if (zoomFactor > 3.0) zoomFactor = 3.0;
            repaint();
        });
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        // فعال سازی آنتی آلیاسینگ برای نرم شدن لبه شکلها
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Hex hex : gameMap.getHexes()) {
            drawHex(g2d, hex);
        }
    }

    private void drawHex(Graphics2D g2d, Hex hex) {
        // تبدیل مختصات محوری به مختصات پیکسلی با فرمول ریاضی
        double x = HEX_SIZE * Math.sqrt(3) * (hex.getQ() + hex.getR() / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * hex.getR();

        // اعمال ضریب زوم و آفست دوربین
        int pixelX = (int) (x * zoomFactor) + offsetX;
        int pixelY = (int) (y * zoomFactor) + offsetY;
        int currentSize = (int) (HEX_SIZE * zoomFactor);

        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle_deg = 60 * i - 30;
            double angle_rad = Math.PI / 180 * angle_deg;
            int px = pixelX + (int) (currentSize * Math.cos(angle_rad));
            int py = pixelY + (int) (currentSize * Math.sin(angle_rad));
            polygon.addPoint(px, py);
        }

        // پیاده سازی بصری مه جنگ
        if (!hex.isExplored() && (hex.getQ() != 0 || hex.getR() != 0)) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillPolygon(polygon);
            g2d.setColor(Color.BLACK);
            g2d.drawPolygon(polygon);
            return;
        }

        // رنگ آمیزی زمینها بر اساس نوع آنها
        switch (hex.getTerrainType()) {
            case FOREST: g2d.setColor(new Color(34, 139, 34)); break;
            case PLAINS: g2d.setColor(new Color(154, 205, 50)); break;
            case MOUNTAIN: g2d.setColor(new Color(139, 137, 137)); break;
            case MEADOW: g2d.setColor(new Color(144, 238, 144)); break;
        }
        g2d.fillPolygon(polygon);

        // رسم خطوط دور هکس
        g2d.setColor(Color.BLACK);
        g2d.drawPolygon(polygon);

        // نمایش بصری منابع روی هکس
        if (hex.getResourceType() != ResourceType.NONE && hex.getResourceAmount() > 0) {
            int fontSize = (int) (14 * zoomFactor);
            if (fontSize > 5) {
                g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
                String resStr = "";
                switch (hex.getResourceType()) {
                    case WOOD: resStr = "W"; g2d.setColor(new Color(101, 67, 33)); break;
                    case STONE: resStr = "S"; g2d.setColor(Color.WHITE); break;
                    case IRON: resStr = "I"; g2d.setColor(Color.ORANGE); break;
                    case FOOD: resStr = "F"; g2d.setColor(Color.YELLOW); break;
                }
                g2d.drawString(resStr, pixelX - fontSize/2, pixelY + fontSize/2);
            }
        }
    }
}