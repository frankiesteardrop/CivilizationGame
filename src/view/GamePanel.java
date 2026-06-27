package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GamePanel extends JPanel {
    private final MainController mainController;

    private final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    private int zoomIndex = 2;
    private double zoomFactor = ZOOM_LEVELS[zoomIndex];

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

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
        setBackground(new Color(20, 25, 30));
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
                        if (clickedHex.getQ() == 0 && clickedHex.getR() == 0 && selectedUnit == null) {
                            showTownHallMenu(e);
                        } else if (selectedUnit != null && selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR()) {
                            showUnitContextMenu(e, clickedHex);
                        } else if (selectedUnit != null && animatingUnit == null) {
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
            if (e.getWheelRotation() < 0 && zoomIndex < ZOOM_LEVELS.length - 1) {
                zoomIndex++;
            } else if (e.getWheelRotation() > 0 && zoomIndex > 0) {
                zoomIndex--;
            }
            zoomFactor = ZOOM_LEVELS[zoomIndex];
            repaint();
        });
    }

    private void showTownHallMenu(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        TownHall th = mainController.getGameMap().getTownHall();
        Inventory inv = th.getInventory();

        JMenuItem whItem = new JMenuItem(th.getWarehouseUpgradeLevel() == 2 ? "Warehouse MAXED" : "Upgrade Warehouse (100 W, 50 S)");
        if (th.getWarehouseUpgradeLevel() == 2 || !inv.hasEnough(ResourceType.WOOD, 100) || !inv.hasEnough(ResourceType.STONE, 50)) whItem.setEnabled(false);
        whItem.addActionListener(ev -> { mainController.getUpgradeController().handleWarehouseUpgrade(); repaint(); });
        popup.add(whItem);

        JMenuItem smItem = new JMenuItem(th.isStoneMineUnlocked() ? "Tech: Stone Mine (Unlocked)" : "Unlock Stone Mine (50 W)");
        if (th.isStoneMineUnlocked() || !inv.hasEnough(ResourceType.WOOD, 50)) smItem.setEnabled(false);
        smItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("STONE_MINE"); repaint(); });
        popup.add(smItem);

        JMenuItem imItem = new JMenuItem(th.isIronMineUnlocked() ? "Tech: Iron Mine (Unlocked)" : "Unlock Iron Mine (100 W, 50 S)");
        if (th.isIronMineUnlocked() || !th.isStoneMineUnlocked() || !inv.hasEnough(ResourceType.WOOD, 100) || !inv.hasEnough(ResourceType.STONE, 50)) imItem.setEnabled(false);
        imItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("IRON_MINE"); repaint(); });
        popup.add(imItem);

        JMenuItem ptItem = new JMenuItem(th.isProfessionalToolsUnlocked() ? "Tech: Prof. Tools (Unlocked)" : "Unlock Prof. Tools (100 W, 100 S, 50 I)");
        if (th.isProfessionalToolsUnlocked() || !th.isIronMineUnlocked() || !inv.hasEnough(ResourceType.WOOD, 100) || !inv.hasEnough(ResourceType.STONE, 100) || !inv.hasEnough(ResourceType.IRON, 50)) ptItem.setEnabled(false);
        ptItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("PROF_TOOLS"); repaint(); });
        popup.add(ptItem);

        JMenuItem setItem = new JMenuItem(th.isSettlementUnlocked() ? "Tech: Settlement (Unlocked)" : "Unlock Settlement (200 W, 100 S)");
        if (th.isSettlementUnlocked() || !inv.hasEnough(ResourceType.WOOD, 200) || !inv.hasEnough(ResourceType.STONE, 100)) setItem.setEnabled(false);
        setItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("SETTLEMENT"); repaint(); });
        popup.add(setItem);

        popup.show(this, e.getX(), e.getY());
    }

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
                    if (!mainController.getBuildController().canBuild(type, hex, builder)) {
                        buildItem.setEnabled(false);
                    }
                    buildItem.addActionListener(ev -> { mainController.getBuildController().buildStructure(builder, type, hex); repaint(); });
                    popup.add(buildItem);
                }
            }
        } else if (selectedUnit instanceof Worker) {
            Worker worker = (Worker) selectedUnit;
            Building building = hex.getBuilding();

            if (building != null) {
                if (!worker.isStationed()) {
                    JMenuItem stationItem = new JMenuItem("Station in " + building.getType().name());
                    if (building.getStationedWorkers() >= building.getMaxWorkers() || worker.getCurrentAP() == 0) stationItem.setEnabled(false);
                    stationItem.addActionListener(ev -> { mainController.getUnitController().handleStation(worker, hex); repaint(); });
                    popup.add(stationItem);
                } else {
                    JMenuItem leaveItem = new JMenuItem("Leave Facility");
                    leaveItem.addActionListener(ev -> { mainController.getUnitController().handleEject(worker); repaint(); });
                    popup.add(leaveItem);
                }
            }
        } else if (selectedUnit instanceof BorderExpander) {
            BorderExpander expander = (BorderExpander) selectedUnit;
            JMenuItem expandItem = new JMenuItem("Expand Border (-" + expander.getMaxAP() + " AP & Consume)");
            if (!hex.isExplored() || expander.getCurrentAP() < expander.getMaxAP()) {
                expandItem.setEnabled(false);
            }
            expandItem.addActionListener(ev -> { expander.expandBorder(mainController.getGameMap()); selectedUnit = null; repaint(); });
            popup.add(expandItem);
        }
        if (popup.getComponentCount() > 0) popup.show(this, e.getX(), e.getY());
    }

    private void selectUnitAt(Hex hex) {
        selectedUnit = null;
        for (Unit u : mainController.getGameMap().getUnits()) {
            // کارگران مستقر شده قابل انتخاب نیستند مگر اینکه در منوی ساختمان خروج زده شود
            if (u instanceof Worker && ((Worker) u).isStationed()) continue;

            if (u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR()) {
                selectedUnit = u; break;
            }
        }
    }

    private void handleMovementCommand(Hex targetHex) {
        if (mainController.getUnitController().canMove(selectedUnit, targetHex, this)) {
            animatingUnit = selectedUnit;
            animStartX = getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR()).x;
            animStartY = getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR()).y;
            animTargetX = getHexPixelCoords(targetHex.getQ(), targetHex.getR()).x;
            animTargetY = getHexPixelCoords(targetHex.getQ(), targetHex.getR()).y;
            animTargetQ = targetHex.getQ();
            animTargetR = targetHex.getR();
            animCost = targetHex.getTerrainType().getMovementCost();
            animProgress = 0.0;
            animationTimer.start();
        }
    }

    private void updateAnimation() {
        animProgress += 0.06;
        if (animProgress >= 1.0) {
            animProgress = 1.0;
            animationTimer.stop();
            if (animatingUnit != null) {
                animatingUnit.moveTo(animTargetQ, animTargetR, animCost);
                mainController.getGameMap().updateFogOfWar();
                animatingUnit = null;
            }
        }
        repaint();
    }

    private Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        return new Point((int) (x * zoomFactor) + offsetX, (int) (y * zoomFactor) + offsetY);
    }

    private Polygon createHexPolygon(Point pt, int size) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle_rad = Math.PI / 180 * (60 * i - 30);
            polygon.addPoint(pt.x + (int) (size * Math.cos(angle_rad)), pt.y + (int) (size * Math.sin(angle_rad)));
        }
        return polygon;
    }

    private Hex getHexAtPixel(Point p) {
        Hex closest = null;
        double minDst = Double.MAX_VALUE;
        for (Hex hex : mainController.getGameMap().getHexes()) {
            double dst = p.distance(getHexPixelCoords(hex.getQ(), hex.getR()));
            if (dst < HEX_SIZE * zoomFactor && dst < minDst) {
                minDst = dst; closest = hex;
            }
        }
        return closest;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        for (Hex hex : mainController.getGameMap().getHexes()) drawHexTerrain(g2d, hex);

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (hex.isInsideBorder() && hex.isExplored()) {
                drawHexBorder(g2d, hex);
            }
        }

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (hex.isExplored()) {
                drawHexDetails(g2d, hex);
            }
        }

        Point thPt = getHexPixelCoords(0, 0);
        g2d.setColor(new Color(255, 215, 0));
        g2d.setFont(new Font("SansSerif", Font.BOLD, (int)(18 * zoomFactor)));
        g2d.drawString("TH", thPt.x - (int)(12 * zoomFactor), thPt.y + (int)(6 * zoomFactor));

        for (Unit u : mainController.getGameMap().getUnits()) {
            if (u.isAlive()) drawUnit(g2d, u);
        }
    }

    private void drawHexTerrain(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int) (HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, currentSize);

        if (!hex.isExplored()) {
            g2d.setColor(new Color(15, 20, 25));
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(new Color(40, 45, 50));
            g2d.drawPolygon(polygon);
            return;
        }

        Color baseColor = Color.BLACK;
        switch (hex.getTerrainType()) {
            case FOREST: baseColor = new Color(46, 125, 50); break;
            case PLAINS: baseColor = new Color(156, 204, 101); break;
            case MOUNTAIN: baseColor = new Color(117, 117, 117); break;
            case MEADOW: baseColor = new Color(129, 199, 132); break;
        }

        // تمایز بصری هکس‌های دارای منبع (GradientPaint)
        if (hex.getResourceType() != ResourceType.NONE && !hex.isResourceDepleted()) {
            GradientPaint gp = new GradientPaint(pt.x, pt.y - currentSize, baseColor.brighter(), pt.x, pt.y + currentSize, baseColor.darker());
            g2d.setPaint(gp);
        } else {
            g2d.setColor(baseColor);
        }
        g2d.fillPolygon(polygon);

        g2d.setStroke(new BasicStroke(1f));
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawPolygon(polygon);
    }

    private void drawHexBorder(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int) (HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, currentSize);

        g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
        g2d.setColor(new Color(255, 215, 0, 200));
        g2d.drawPolygon(polygon);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawHexDetails(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int fontSize = (int) (14 * zoomFactor);
        int rSize = (int)(16 * zoomFactor);

        if (hex.getResourceType() != ResourceType.NONE) {
            if (hex.isResourceDepleted()) {
                // گرافیک اتمام منبع (X قرمز)
                g2d.setColor(new Color(50, 50, 50, 180));
                g2d.fillOval(pt.x - rSize/2, pt.y - (int)(15 * zoomFactor) - rSize/2, rSize, rSize);
                g2d.setColor(new Color(220, 20, 60)); // Crimson Red
                g2d.setStroke(new BasicStroke(2f));
                g2d.drawLine(pt.x - rSize/4, pt.y - (int)(15 * zoomFactor) - rSize/4, pt.x + rSize/4, pt.y - (int)(15 * zoomFactor) + rSize/4);
                g2d.drawLine(pt.x + rSize/4, pt.y - (int)(15 * zoomFactor) - rSize/4, pt.x - rSize/4, pt.y - (int)(15 * zoomFactor) + rSize/4);
                g2d.setStroke(new BasicStroke(1f));
            } else if (fontSize > 6) {
                // رندرینگ منبع سالم
                g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                String resStr = "";
                Color iconColor = Color.WHITE;

                switch (hex.getResourceType()) {
                    case WOOD: resStr = "W"; iconColor = new Color(139, 69, 19); break;
                    case STONE: resStr = "S"; iconColor = new Color(200, 200, 200); break;
                    case IRON: resStr = "I"; iconColor = new Color(255, 140, 0); break;
                    case FOOD: resStr = "F"; iconColor = new Color(255, 215, 0); break;
                }

                g2d.setColor(new Color(0, 0, 0, 150));
                g2d.fillOval(pt.x - rSize/2, pt.y - (int)(15 * zoomFactor) - rSize/2, rSize, rSize);
                g2d.setColor(iconColor);
                g2d.drawString(resStr, pt.x - fontSize/3, pt.y - (int)(15 * zoomFactor) + fontSize/3);
            }
        }

        if (hex.getBuilding() != null) {
            drawBuildingIcon(g2d, hex.getBuilding(), pt, (int)(18 * zoomFactor));
        }
    }

    private void drawBuildingIcon(Graphics2D g2d, Building b, Point pt, int size) {
        int x = pt.x;
        int y = pt.y + 2;

        // سایه ساختمان
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRect(x - size/2 + 2, y + 2, size, size);

        if (b.getType() == BuildingType.SETTLEMENT) {
            g2d.setColor(new Color(138, 43, 226)); // Purple
            Polygon house = new Polygon();
            house.addPoint(x, y - size/2);
            house.addPoint(x + size/2, y);
            house.addPoint(x + size/2, y + size/2);
            house.addPoint(x - size/2, y + size/2);
            house.addPoint(x - size/2, y);
            g2d.fillPolygon(house);
            g2d.setColor(Color.WHITE);
            g2d.drawPolygon(house);
        } else if (b.getType() == BuildingType.FARM) {
            g2d.setColor(new Color(255, 215, 0));
            g2d.fillRect(x - size/2, y - size/4, size, size/2);
            g2d.setColor(new Color(139, 69, 19));
            g2d.drawRect(x - size/2, y - size/4, size, size/2);
            g2d.drawLine(x - size/4, y - size/4, x - size/4, y + size/4);
            g2d.drawLine(x + size/4, y - size/4, x + size/4, y + size/4);
        } else if (b.getType() == BuildingType.LUMBER_MILL) {
            g2d.setColor(new Color(139, 69, 19));
            g2d.fillRect(x - size/2, y, size, size/2);
            g2d.setColor(Color.LIGHT_GRAY);
            g2d.fillOval(x - size/4, y - size/4, size/2, size/2);
            g2d.setColor(Color.BLACK);
            g2d.drawOval(x - size/4, y - size/4, size/2, size/2);
        } else {
            g2d.setColor(Color.DARK_GRAY);
            Polygon mine = new Polygon();
            mine.addPoint(x, y - size/2);
            mine.addPoint(x + size/2, y + size/2);
            mine.addPoint(x - size/2, y + size/2);
            g2d.fillPolygon(mine);
            g2d.setColor(Color.BLACK);
            g2d.fillArc(x - size/4, y, size/2, size, 0, 180);
        }

        int fontSize = (int) (14 * zoomFactor);
        if (fontSize > 6) {
            g2d.setFont(new Font("SansSerif", Font.PLAIN, fontSize - 3));
            String workerText = b.getStationedWorkers() + "/" + b.getMaxWorkers();
            g2d.setColor(new Color(0, 0, 0, 180));
            g2d.fillRect(x - size/2 - 2, y + size/2 + 2, size + 10, fontSize);
            g2d.setColor(Color.WHITE);
            g2d.drawString(workerText, x - size/2, y + size/2 + fontSize - 1);
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u) {
        boolean isStationed = (u instanceof Worker && ((Worker) u).isStationed());

        Hex unitHex = mainController.getGameMap().getHexAt(u.getQ(), u.getR());
        if (unitHex != null && !unitHex.isExplored()) return;

        int px, py;
        if (u == animatingUnit) {
            double easeOut = 1.0 - Math.pow(1.0 - animProgress, 3);
            px = (int) (animStartX + (animTargetX - animStartX) * easeOut);
            py = (int) (animStartY + (animTargetY - animStartY) * easeOut);
        } else {
            Point pt = getHexPixelCoords(u.getQ(), u.getR());
            px = pt.x;
            py = pt.y;
        }

        int radius = Math.max(6, (int) (15 * zoomFactor));
        String typeLetter = "U";
        Color unitColor = Color.GRAY;

        if (u instanceof Explorer) { unitColor = new Color(65, 105, 225); typeLetter = "E"; }
        else if (u instanceof Builder) { unitColor = new Color(255, 215, 0); typeLetter = "B"; }
        else if (u instanceof Worker) { unitColor = new Color(255, 140, 0); typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = new Color(138, 43, 226); typeLetter = "X"; }

        // منطق جدید برای نمایش کارگران مستقر به صورت چسبیده به ساختمان
        if (isStationed) {
            radius = Math.max(4, (int)(8 * zoomFactor)); // کوچک‌تر رسم می‌شود
            px += (int)(15 * zoomFactor); // افست به گوشه هکس
            py -= (int)(5 * zoomFactor);
        }

        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillOval(px - radius + 2, py - radius + 2, radius * 2, radius * 2);

        g2d.setColor(unitColor);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setColor(Color.WHITE);
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);

        int fontSize = (int) (12 * zoomFactor);
        if (fontSize > 6 && !isStationed) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();
            String text = typeLetter + u.getCurrentAP();
            int txtX = px - fm.stringWidth(text) / 2;
            int txtY = py + fm.getAscent() / 2 - 2;
            g2d.drawString(text, txtX, txtY);
        }

        if (u == selectedUnit && !isStationed) {
            g2d.setColor(new Color(0, 255, 255, 150));
            g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
            g2d.drawOval(px - radius - 4, py - radius - 4, radius * 2 + 8, radius * 2 + 8);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}