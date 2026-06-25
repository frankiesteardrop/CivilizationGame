package view;

import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * پنل گرافیکی اصلی بازی جهت نمایش نقشه هگز، انیمیشن حرکت و منوهای تعاملی.
 */
public class GamePanel extends JPanel {
    private final GameMap gameMap;
    private double zoomFactor = 1.0;
    private int offsetX = 400;
    private int offsetY = 300;
    private Point lastMousePosition;
    private final int HEX_SIZE = 40;

    private Unit selectedUnit = null;
    private Unit animatingUnit = null;
    private double animProgress = 0.0;
    private int animStartX, animStartY, animTargetX, animTargetY;
    private int animTargetQ, animTargetR, animCost;
    private final Timer animationTimer;

    public GamePanel(GameMap gameMap) {
        this.gameMap = gameMap;
        setBackground(Color.BLACK);
        setFocusable(true);

        animationTimer = new Timer(16, e -> updateAnimation());

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                Hex clickedHex = getHexAtPixel(e.getPoint());

                if (clickedHex != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectUnitAt(clickedHex);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // باز کردن منوی عملیات اختصاصی در صورت کلیک راست روی خود یونیت منتخب
                        if (selectedUnit != null && selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR()) {
                            showUnitContextMenu(e, clickedHex);
                        }
                        // حرکت دادن یونیت در صورت کلیک راست روی خانه‌های مجاور
                        else if (selectedUnit != null && animatingUnit == null) {
                            handleMovementCommand(clickedHex);
                        }
                    }
                }
                repaint();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    offsetX += e.getX() - lastMousePosition.x;
                    offsetY += e.getY() - lastMousePosition.y;
                    lastMousePosition = e.getPoint();
                    repaint();
                }
            }
        };
        addMouseListener(mouseAdapter);
        addMouseMotionListener(mouseAdapter);

        addMouseWheelListener(e -> {
            if (e.getWheelRotation() < 0) zoomFactor += 0.2;
            else zoomFactor -= 0.2;
            zoomFactor = Math.max(0.4, Math.min(3.0, zoomFactor));
            repaint();
        });
    }

    // --- سیستم منوی تعاملی راست کلیک یونیت‌ها (Context Menu) ---
    private void showUnitContextMenu(MouseEvent e, Hex hex) {
        JPopupMenu popup = new JPopupMenu();

        if (selectedUnit instanceof Builder) {
            Builder builder = (Builder) selectedUnit;
            if (hex.getBuilding() != null) {
                JMenuItem errorItem = new JMenuItem("Hex already occupied");
                errorItem.setEnabled(false);
                popup.add(errorItem);
            } else {
                for (BuildingType type : BuildingType.values()) {
                    JMenuItem buildItem = new JMenuItem("Build " + type.name() + " (-" + type.getApCost() + " AP)");

                    boolean validTerrain = checkTerrainForBuilding(type, hex);
                    Inventory inv = gameMap.getTownHall().getInventory();
                    boolean hasResources = inv.hasEnough(ResourceType.WOOD, type.getWoodCost()) &&
                            inv.hasEnough(ResourceType.STONE, type.getStoneCost()) &&
                            inv.hasEnough(ResourceType.IRON, type.getIronCost());
                    boolean hasAP = builder.getCurrentAP() >= type.getApCost();

                    // غیرفعال کردن گزینه در صورت عدم احراز شرایط داک پروژه
                    if (!validTerrain || !hasResources || !hasAP || builder.getCharges() <= 0) {
                        buildItem.setEnabled(false);
                    }

                    buildItem.addActionListener(ev -> {
                        inv.consumeResource(ResourceType.WOOD, type.getWoodCost());
                        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
                        inv.consumeResource(ResourceType.IRON, type.getIronCost());
                        builder.consumeAP(type.getApCost());
                        builder.useCharge();

                        hex.setBuilding(new Building(type));
                        repaint();
                    });
                    popup.add(buildItem);
                }
            }
        } else if (selectedUnit instanceof Worker) {
            Worker worker = (Worker) selectedUnit;
            Building building = hex.getBuilding();

            if (building != null) {
                if (!worker.isStationed()) {
                    JMenuItem stationItem = new JMenuItem("Station in " + building.getType().name());
                    if (building.getStationedWorkers() >= building.getType().getMaxWorkers() || worker.getCurrentAP() == 0) {
                        stationItem.setEnabled(false);
                    }
                    stationItem.addActionListener(ev -> {
                        building.addWorker();
                        worker.setStationed(true);
                        worker.consumeAP(worker.getCurrentAP()); // قفل کردن کارگر در این نوبت
                        repaint();
                    });
                    popup.add(stationItem);
                } else {
                    JMenuItem leaveItem = new JMenuItem("Leave Facility");
                    leaveItem.addActionListener(ev -> {
                        building.removeWorker();
                        worker.setStationed(false);
                        repaint();
                    });
                    popup.add(leaveItem);
                }
            } else {
                JMenuItem errorItem = new JMenuItem("No facility to station");
                errorItem.setEnabled(false);
                popup.add(errorItem);
            }
        }

        if (popup.getComponentCount() > 0) {
            popup.show(this, e.getX(), e.getY());
        }
    }

    private boolean checkTerrainForBuilding(BuildingType type, Hex hex) {
        switch (type) {
            case LUMBER_MILL: return hex.getTerrainType() == TerrainType.FOREST;
            case STONE_MINE: return hex.getTerrainType() == TerrainType.MOUNTAIN && hex.getResourceType() == ResourceType.STONE;
            case IRON_MINE: return hex.getTerrainType() == TerrainType.MOUNTAIN && hex.getResourceType() == ResourceType.IRON;
            case FARM: return hex.getTerrainType() == TerrainType.MEADOW && hex.getResourceType() == ResourceType.FOOD;
            case STABLE: return hex.getTerrainType() == TerrainType.PLAINS && hex.getResourceType() == ResourceType.FOOD;
            case SETTLEMENT: return hex.getResourceType() == ResourceType.NONE;
            default: return false;
        }
    }

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
        if (selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed()) {
            JOptionPane.showMessageDialog(this, "Worker is stationed. Leave facility first!");
            return;
        }

        int dq = targetHex.getQ() - selectedUnit.getQ();
        int dr = targetHex.getR() - selectedUnit.getR();
        boolean isNeighbor = (Math.abs(dq) <= 1 && Math.abs(dr) <= 1 && Math.abs(dq + dr) <= 1) && !(dq == 0 && dr == 0);

        if (!isNeighbor) {
            JOptionPane.showMessageDialog(this, "Movement is only allowed to adjacent hexes!");
            return;
        }

        int cost = targetHex.getTerrainType().getMovementCost();
        if (selectedUnit.getCurrentAP() < cost) {
            JOptionPane.showMessageDialog(this, "Not enough Action Points (AP)! Need " + cost);
            return;
        }

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
        animProgress += 0.08;
        if (animProgress >= 1.0) {
            animProgress = 1.0;
            animationTimer.stop();
            if (animatingUnit != null) {
                animatingUnit.moveTo(animTargetQ, animTargetR, animCost);
                gameMap.updateFogOfWar();
                animatingUnit = null;
            }
        }
        repaint();
    }

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

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (Hex hex : gameMap.getHexes()) {
            drawHex(g2d, hex);
        }

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

        if (!hex.isExplored()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillPolygon(polygon);
            g2d.setColor(Color.BLACK);
            g2d.drawPolygon(polygon);
            return;
        }

        switch (hex.getTerrainType()) {
            case FOREST: g2d.setColor(new Color(34, 139, 34)); break;
            case PLAINS: g2d.setColor(new Color(154, 205, 50)); break;
            case MOUNTAIN: g2d.setColor(new Color(139, 137, 137)); break;
            case MEADOW: g2d.setColor(new Color(144, 238, 144)); break;
        }
        g2d.fillPolygon(polygon);
        g2d.setColor(Color.BLACK);
        g2d.drawPolygon(polygon);

        int fontSize = (int) (14 * zoomFactor);

        // رسم بصری منابع طبیعی روی نقشه
        if (hex.getResourceType() != ResourceType.NONE && hex.getResourceAmount() > 0) {
            if (fontSize > 5) {
                g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
                String resStr = "";
                switch (hex.getResourceType()) {
                    case WOOD: resStr = "W"; g2d.setColor(new Color(101, 67, 33)); break;
                    case STONE: resStr = "S"; g2d.setColor(Color.WHITE); break;
                    case IRON: resStr = "I"; g2d.setColor(Color.ORANGE); break;
                    case FOOD: resStr = "F"; g2d.setColor(Color.YELLOW); break;
                }
                g2d.drawString(resStr, pt.x - fontSize/2, pt.y - 5);
            }
        }

        // رسم گرافیکی سازه ساخته شده و وضعیت تعداد کارگران درون آن
        if (hex.getBuilding() != null) {
            g2d.setColor(Color.PINK);
            int bSize = (int) (16 * zoomFactor);
            g2d.fillRect(pt.x - bSize/2, pt.y + 5, bSize, bSize);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(pt.x - bSize/2, pt.y + 5, bSize, bSize);

            if (fontSize > 5) {
                g2d.setFont(new Font("Arial", Font.PLAIN, fontSize - 2));
                g2d.drawString(hex.getBuilding().getStationedWorkers() + "/" + hex.getBuilding().getType().getMaxWorkers(), pt.x - bSize/2, pt.y + bSize + 15);
            }
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u) {
        Hex unitHex = gameMap.getHexAt(u.getQ(), u.getR());
        if (unitHex != null && !unitHex.isExplored()) return;

        // کارگر مستقر شده داخل ساختمان رسم نمی‌شود تا محیط بصری خلوت بماند
        if (u instanceof Worker && ((Worker) u).isStationed()) return;

        int px, py;
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

        String typeLetter = "U";
        if (u instanceof model.Explorer) { g2d.setColor(new Color(65, 105, 225)); typeLetter = "E"; }
        else if (u instanceof model.Builder) { g2d.setColor(new Color(255, 215, 0)); typeLetter = "B"; }
        else if (u instanceof model.Worker) { g2d.setColor(new Color(255, 140, 0)); typeLetter = "W"; }

        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setColor(Color.BLACK);
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);

        int fontSize = (int) (11 * zoomFactor);
        if (fontSize > 5) {
            g2d.setFont(new Font("Arial", Font.BOLD, fontSize));
            g2d.setColor(Color.BLACK);
            g2d.drawString(typeLetter + u.getCurrentAP(), px - radius/2, py + radius/2);
        }

        if (u == selectedUnit) {
            g2d.setColor(Color.CYAN);
            g2d.setStroke(new BasicStroke(2f));
            g2d.drawOval(px - radius - 3, py - radius - 3, radius * 2 + 6, radius * 2 + 6);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}