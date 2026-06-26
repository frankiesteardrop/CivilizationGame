package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

public class GamePanel extends JPanel {
    private final MainController mainController;
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

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
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
            if (e.getWheelRotation() < 0) zoomFactor += 0.2;
            else zoomFactor -= 0.2;
            zoomFactor = Math.max(0.4, Math.min(3.0, zoomFactor));
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
        }
        if (popup.getComponentCount() > 0) popup.show(this, e.getX(), e.getY());
    }

    private void selectUnitAt(Hex hex) {
        selectedUnit = null;
        for (Unit u : mainController.getGameMap().getUnits()) {
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
        animProgress += 0.08;
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

        for (Hex hex : mainController.getGameMap().getHexes()) drawHex(g2d, hex);

        Point thPt = getHexPixelCoords(0, 0);
        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font("Arial", Font.BOLD, (int)(16 * zoomFactor)));
        g2d.drawString("TH", thPt.x - (int)(10 * zoomFactor), thPt.y + (int)(5 * zoomFactor));

        for (Unit u : mainController.getGameMap().getUnits()) if (u.isAlive()) drawUnit(g2d, u);
    }

    private void drawHex(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int) (HEX_SIZE * zoomFactor);

        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle_rad = Math.PI / 180 * (60 * i - 30);
            polygon.addPoint(pt.x + (int) (currentSize * Math.cos(angle_rad)), pt.y + (int) (currentSize * Math.sin(angle_rad)));
        }

        if (!hex.isExplored()) {
            g2d.setColor(Color.DARK_GRAY); g2d.fillPolygon(polygon);
            g2d.setColor(Color.BLACK); g2d.drawPolygon(polygon); return;
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
        if (hex.getResourceType() != ResourceType.NONE && hex.getResourceAmount() > 0 && fontSize > 5) {
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

        if (hex.getBuilding() != null) {
            g2d.setColor(Color.PINK);
            int bSize = (int) (16 * zoomFactor);
            g2d.fillRect(pt.x - bSize/2, pt.y + 5, bSize, bSize);
            g2d.setColor(Color.BLACK);
            g2d.drawRect(pt.x - bSize/2, pt.y + 5, bSize, bSize);
            if (fontSize > 5) {
                g2d.setFont(new Font("Arial", Font.PLAIN, fontSize - 2));
                g2d.drawString(hex.getBuilding().getStationedWorkers() + "/" + hex.getBuilding().getMaxWorkers(), pt.x - bSize/2, pt.y + bSize + 15);
            }
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u) {
        if (u instanceof Worker && ((Worker) u).isStationed()) return;
        Hex unitHex = mainController.getGameMap().getHexAt(u.getQ(), u.getR());
        if (unitHex != null && !unitHex.isExplored()) return;

        int px = (u == animatingUnit) ? (int) (animStartX + (animTargetX - animStartX) * animProgress) : getHexPixelCoords(u.getQ(), u.getR()).x;
        int py = (u == animatingUnit) ? (int) (animStartY + (animTargetY - animStartY) * animProgress) : getHexPixelCoords(u.getQ(), u.getR()).y;

        int radius = Math.max(4, (int) (15 * zoomFactor));
        String typeLetter = "U";
        if (u instanceof Explorer) { g2d.setColor(new Color(65, 105, 225)); typeLetter = "E"; }
        else if (u instanceof Builder) { g2d.setColor(new Color(255, 215, 0)); typeLetter = "B"; }
        else if (u instanceof Worker) { g2d.setColor(new Color(255, 140, 0)); typeLetter = "W"; }
        else if (u instanceof BorderExpander) { g2d.setColor(new Color(138, 43, 226)); typeLetter = "X"; }

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