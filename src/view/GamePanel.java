package view;

import model.GameMap;
import model.Hex;
import model.ResourceType;
import model.Unit;

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

    // متغیرهای مربوط به انتخاب و انیمیشن یونیت‌ها
    private Unit selectedUnit = null;
    private Unit animatingUnit = null;
    private double animProgress = 0.0;
    private int animStartX, animStartY, animTargetX, animTargetY;
    private int animTargetQ, animTargetR, animCost;
    private Timer animationTimer;

    public GamePanel(GameMap gameMap) {
        this.gameMap = gameMap;
        setBackground(Color.BLACK); // پس زمینه تاریک بیرون نقشه
        setFocusable(true);

        // راه اندازی تایمر انیمیشن حرکت (حدود 60 فریم بر ثانیه)
        animationTimer = new Timer(16, e -> updateAnimation());

        // پیاده سازی جابجایی دوربین، انتخاب یونیت و حرکت
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                Hex clickedHex = getHexAtPixel(e.getPoint());

                if (clickedHex != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        // کلیک چپ: انتخاب یونیت
                        selectUnitAt(clickedHex);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // کلیک راست: حرکت یونیت انتخاب شده
                        if (selectedUnit != null && animatingUnit == null) {
                            handleMovementCommand(clickedHex);
                        }
                    }
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                // جابجایی نقشه فقط با کلیک چپ (در صورت درگ کردن)
                if (SwingUtilities.isLeftMouseButton(e)) {
                    int dx = e.getX() - lastMousePosition.x;
                    int dy = e.getY() - lastMousePosition.y;
                    offsetX += dx;
                    offsetY += dy;
                    lastMousePosition = e.getPoint();
                    repaint();
                }
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
            if (zoomFactor < 0.4) zoomFactor = 0.4;
            if (zoomFactor > 3.0) zoomFactor = 3.0;
            repaint();
        });
    }

    // --- منطق انتخاب و حرکت یونیت‌ها ---

    private void selectUnitAt(Hex hex) {
        selectedUnit = null;
        for (Unit u : gameMap.getUnits()) {
            if (u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR()) {
                selectedUnit = u;
                break;
            }
        }
    }

    private void handleMovementCommand(Hex targetHex) {
        // ۱. بررسی همسایه بودن هکس مقصد در سیستم مختصات محوری
        int dq = targetHex.getQ() - selectedUnit.getQ();
        int dr = targetHex.getR() - selectedUnit.getR();
        boolean isNeighbor = (Math.abs(dq) <= 1 && Math.abs(dr) <= 1 && Math.abs(dq + dr) <= 1) && !(dq == 0 && dr == 0);

        if (!isNeighbor) {
            JOptionPane.showMessageDialog(this, "Movement is only allowed to adjacent hexes!");
            return;
        }

        // ۲. دریافت هزینه حرکت از نوع زمین مقصد
        int cost = targetHex.getTerrainType().getMovementCost();

        if (selectedUnit.getCurrentAP() < cost) {
            JOptionPane.showMessageDialog(this, "Not enough Action Points (AP)! Need " + cost);
            return;
        }

        // ۳. شروع انیمیشن
        Point startPt = getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
        Point targetPt = getHexPixelCoords(targetHex.getQ(), targetHex.getR());

        animatingUnit = selectedUnit;
        animStartX = startPt.x;
        animStartY = startPt.y;
        animTargetX = targetPt.x;
        animTargetY = targetPt.y;
        animTargetQ = targetHex.getQ();
        animTargetR = targetHex.getR();
        animCost = cost;
        animProgress = 0.0;

        animationTimer.start();
    }

    private void updateAnimation() {
        animProgress += 0.08; // سرعت انیمیشن
        if (animProgress >= 1.0) {
            animProgress = 1.0;
            animationTimer.stop();
            if (animatingUnit != null) {
                // اعمال قطعی حرکت و کسر AP پس از اتمام انیمیشن
                animatingUnit.moveTo(animTargetQ, animTargetR, animCost);
                animatingUnit = null;
            }
        }
        repaint();
    }

    // --- توابع کمکی محاسبات مختصات ---

    private Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        int pixelX = (int) (x * zoomFactor) + offsetX;
        int pixelY = (int) (y * zoomFactor) + offsetY;
        return new Point(pixelX, pixelY);
    }

    private Hex getHexAtPixel(Point p) {
        Hex closest = null;
        double minDst = Double.MAX_VALUE;
        for (Hex hex : gameMap.getHexes()) {
            Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
            double dst = p.distance(pt);
            if (dst < HEX_SIZE * zoomFactor && dst < minDst) {
                minDst = dst;
                closest = hex;
            }
        }
        return closest;
    }

    // --- رسم گرافیک ---

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // رسم تمام هکس‌ها
        for (Hex hex : gameMap.getHexes()) {
            drawHex(g2d, hex);
        }

        // رسم یونیت‌ها روی نقشه
        for (Unit u : gameMap.getUnits()) {
            if (u.isAlive()) {
                drawUnit(g2d, u);
            }
        }
    }

    private void drawHex(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int) (HEX_SIZE * zoomFactor);

        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle_deg = 60 * i - 30;
            double angle_rad = Math.PI / 180 * angle_deg;
            int px = pt.x + (int) (currentSize * Math.cos(angle_rad));
            int py = pt.y + (int) (currentSize * Math.sin(angle_rad));
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
                g2d.drawString(resStr, pt.x - fontSize/2, pt.y + fontSize/2);
            }
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u) {
        int px, py;

        // محاسبه مختصات در حین پخش انیمیشن
        if (u == animatingUnit) {
            px = (int) (animStartX + (animTargetX - animStartX) * animProgress);
            py = (int) (animStartY + (animTargetY - animStartY) * animProgress);
        } else {
            Point pt = getHexPixelCoords(u.getQ(), u.getR());
            px = pt.x;
            py = pt.y;
        }

        int radius = (int) (15 * zoomFactor);
        if (radius < 4) radius = 4;

        // مشخص کردن رنگ و حرف نماینده هر یونیت
        String typeLetter = "U";
        if (u instanceof model.Explorer) { g2d.setColor(new Color(65, 105, 225)); typeLetter = "E"; }
        else if (u instanceof model.Builder) { g2d.setColor(new Color(255, 215, 0)); typeLetter = "B"; }
        else if (u instanceof model.Worker) { g2d.setColor(new Color(255, 140, 0)); typeLetter = "W"; }

        // رسم دایره اصلی یونیت
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);

        // چاپ مقدار AP داخل یونیت
        int fontSize = (int) (11 * zoomFactor);
        if (fontSize > 5) {
            g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
            g2d.setColor(Color.BLACK);
            g2d.drawString(typeLetter + u.getCurrentAP(), px - radius/2, py + radius/2);
        }

        // افکت انتخاب شدن (هایلایت فیروزه‌ای دور یونیت)
        if (u == selectedUnit) {
            g2d.setColor(Color.CYAN);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval(px - radius - 3, py - radius - 3, radius * 2 + 6, radius * 2 + 6);
            g2d.setStroke(new BasicStroke(1f)); // بازگرداندن ضخامت خط به حالت عادی
        }
    }
}