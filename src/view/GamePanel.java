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

    private Hex hoveredHex = null;
    private double pulseScale = 1.0;
    private boolean pulseGrowing = true;

    private final Timer animationTimer;

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
        setBackground(new Color(15, 18, 22));
        setFocusable(true);
        ToolTipManager.sharedInstance().setInitialDelay(200);

        animationTimer = new Timer(16, e -> {
            updateAnimation();
            updatePulseEffect();
            repaint();
        });
        animationTimer.start();

        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                Hex clickedHex = getHexAtPixel(e.getPoint());

                if (clickedHex != null) {
                    if (SwingUtilities.isLeftMouseButton(e)) {
                        selectUnitAt(clickedHex);
                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        // اصلاح: اگر روی ساختمان تان‌هال کلیک شد، منوی آن باز شود!
                        if (clickedHex.getBuilding() != null && clickedHex.getBuilding().getType() == BuildingType.TOWN_HALL && selectedUnit == null) {
                            showTownHallMenu(e);
                        } else if (selectedUnit != null &&
                                selectedUnit.getQ() == clickedHex.getQ() &&
                                selectedUnit.getR() == clickedHex.getR()) {
                            showUnitContextMenu(e, clickedHex);
                        } else if (selectedUnit != null && animatingUnit == null) {
                            if (!(selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed())) {
                                handleMovementCommand(clickedHex);
                            }
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

            @Override
            public void mouseMoved(MouseEvent e) {
                Hex currentHover = getHexAtPixel(e.getPoint());
                if (currentHover != hoveredHex) {
                    hoveredHex = currentHover;
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

    @Override
    public String getToolTipText(MouseEvent e) {
        Hex hex = getHexAtPixel(e.getPoint());
        if (hex == null || !hex.isExplored()) return null;

        StringBuilder html = new StringBuilder("<html><body style='background-color:#2b2b2b; color:white; padding:5px; font-family:sans-serif;'>");
        html.append("<b style='color:#00BFFF;'>").append(hex.getTerrainType().name()).append("</b><br/>");

        if (hex.getResourceType() != ResourceType.NONE) {
            html.append("Resource: ").append(hex.getResourceType().name());
            if (hex.isResourceDepleted()) html.append(" <b style='color:red;'>(DEPLETED)</b>");
            html.append("<br/>");
        }

        Building b = hex.getBuilding();
        if (b != null) {
            html.append("<hr/><b style='color:#FFD700;'>").append(b.getType().name()).append("</b><br/>");
            if (b.isDestroyed()) {
                html.append("<span style='color:red;'>DESTROYED</span>");
            } else if (b.getType() != BuildingType.TOWN_HALL) {
                html.append("Workers: ").append(b.getStationedWorkers()).append("/").append(b.getMaxWorkers()).append("<br/>");
                html.append("Production: +").append(b.calculateProduction()).append("/Turn");
            }
        }

        for (Unit u : mainController.getGameMap().getUnits()) {
            if (u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR()) {
                html.append("<hr/><b style='color:#32CD32;'>").append(u.getClass().getSimpleName()).append("</b><br/>");
                html.append("AP: ").append(u.getCurrentAP()).append("/").append(u.getMaxAP());
                if (u instanceof Builder) html.append("<br/>Charges: ").append(((Builder) u).getCharges());
                if (u instanceof Worker) {
                    Worker w = (Worker) u;
                    if (w.isStationed()) {
                        html.append(" <span style='color:orange;'>(Stationed - AP locked)</span>");
                    }
                }
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private void updatePulseEffect() {
        if (pulseGrowing) {
            pulseScale += 0.015;
            if (pulseScale >= 1.25) pulseGrowing = false;
        } else {
            pulseScale -= 0.015;
            if (pulseScale <= 1.0) pulseGrowing = true;
        }
    }

    private void showTownHallMenu(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        TownHall th = mainController.getGameMap().getTownHall();

        JMenuItem whItem = new JMenuItem(th.getWarehouseUpgradeLevel() == 2
                ? "✅ Warehouse MAXED"
                : "Upgrade Warehouse (100 W, 50 S)");
        if (!mainController.getUpgradeController().canAffordWarehouseUpgrade()) whItem.setEnabled(false);
        whItem.addActionListener(ev -> { mainController.getUpgradeController().handleWarehouseUpgrade(); repaint(); });
        popup.add(whItem);

        popup.addSeparator();

        JMenuItem smItem = new JMenuItem(th.isStoneMineUnlocked()
                ? "✅ Tech: Stone Mine (Unlocked)"
                : "🔬 Unlock Stone Mine (50 W)");
        if (!mainController.getUpgradeController().canUnlockTech("STONE_MINE")) smItem.setEnabled(false);
        smItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("STONE_MINE"); repaint(); });
        popup.add(smItem);

        JMenuItem imItem = new JMenuItem(th.isIronMineUnlocked()
                ? "✅ Tech: Iron Mine (Unlocked)"
                : "🔬 Unlock Iron Mine (100 W, 50 S)");
        if (!mainController.getUpgradeController().canUnlockTech("IRON_MINE")) imItem.setEnabled(false);
        imItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("IRON_MINE"); repaint(); });
        popup.add(imItem);

        JMenuItem ptItem = new JMenuItem(th.isProfessionalToolsUnlocked()
                ? "✅ Tech: Prof. Tools (Unlocked)"
                : "🔬 Unlock Prof. Tools (100 W, 100 S, 50 I)");
        if (!mainController.getUpgradeController().canUnlockTech("PROF_TOOLS")) ptItem.setEnabled(false);
        ptItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("PROF_TOOLS"); repaint(); });
        popup.add(ptItem);

        JMenuItem setItem = new JMenuItem(th.isSettlementUnlocked()
                ? "✅ Tech: Settlement (Unlocked)"
                : "🔬 Unlock Settlement (200 W, 100 S)");
        if (!mainController.getUpgradeController().canUnlockTech("SETTLEMENT")) setItem.setEnabled(false);
        setItem.addActionListener(ev -> { mainController.getUpgradeController().unlockTech("SETTLEMENT"); repaint(); });
        popup.add(setItem);

        popup.addSeparator();

        JMenuItem tWorker = new JMenuItem("👷 Train Worker (20 F) — 1 Turn");
        if (!mainController.getUpgradeController().canTrainUnit("WORKER")) tWorker.setEnabled(false);
        tWorker.addActionListener(ev -> { mainController.getUpgradeController().trainUnit("WORKER"); repaint(); });
        popup.add(tWorker);

        JMenuItem tBuilder = new JMenuItem("🔨 Train Builder (30 F, 10 W) — 2 Turns");
        if (!mainController.getUpgradeController().canTrainUnit("BUILDER")) tBuilder.setEnabled(false);
        tBuilder.addActionListener(ev -> { mainController.getUpgradeController().trainUnit("BUILDER"); repaint(); });
        popup.add(tBuilder);

        JMenuItem tExplorer = new JMenuItem("🧭 Train Explorer (50 F) — 3 Turns");
        if (!mainController.getUpgradeController().canTrainUnit("EXPLORER")) tExplorer.setEnabled(false);
        tExplorer.addActionListener(ev -> { mainController.getUpgradeController().trainUnit("EXPLORER"); repaint(); });
        popup.add(tExplorer);

        JMenuItem tExpander = new JMenuItem("🗺️ Train Border Expander (40 F, 20 W) — 3 Turns");
        if (!mainController.getUpgradeController().canTrainUnit("BORDER_EXPANDER")) tExpander.setEnabled(false);
        tExpander.addActionListener(ev -> { mainController.getUpgradeController().trainUnit("BORDER_EXPANDER"); repaint(); });
        popup.add(tExpander);

        popup.show(this, e.getX(), e.getY());
    }

    private void showUnitContextMenu(MouseEvent e, Hex hex) {
        if (selectedUnit == null) return;

        JPopupMenu popup = new JPopupMenu();

        if (selectedUnit instanceof Builder) {
            Builder builder = (Builder) selectedUnit;

            if (hex.getBuilding() != null) {
                JMenuItem occupiedItem = new JMenuItem("⛔ Hex already has a building");
                occupiedItem.setEnabled(false);
                popup.add(occupiedItem);
            } else if (!hex.isInsideBorder()) {
                JMenuItem borderItem = new JMenuItem("⛔ Must be inside your borders");
                borderItem.setEnabled(false);
                popup.add(borderItem);
            } else {
                popup.add(createBuildMenuItem(builder, hex, BuildingType.LUMBER_MILL, "🌲 Build Lumber Mill"));
                popup.add(createBuildMenuItem(builder, hex, BuildingType.FARM, "🌾 Build Farm"));
                popup.add(createBuildMenuItem(builder, hex, BuildingType.STABLE, "🐄 Build Stable"));
                popup.add(createBuildMenuItem(builder, hex, BuildingType.STONE_MINE, "⛏️ Build Stone Mine"));
                popup.add(createBuildMenuItem(builder, hex, BuildingType.IRON_MINE, "🔩 Build Iron Mine"));
                popup.add(createBuildMenuItem(builder, hex, BuildingType.SETTLEMENT, "🏘️ Build Settlement"));
            }

        } else if (selectedUnit instanceof Worker) {
            Worker worker = (Worker) selectedUnit;

            if (worker.isStationed()) {
                JMenuItem leaveItem = new JMenuItem("🚪 Leave Facility (AP not restored)");
                leaveItem.addActionListener(ev -> {
                    mainController.getUnitController().handleEject(worker);
                    repaint();
                });
                popup.add(leaveItem);

            } else {
                Building building = hex.getBuilding();
                if (building != null && !building.isDestroyed() && building.getType() != BuildingType.TOWN_HALL) {
                    boolean canStation = mainController.getUnitController().canStation(worker, hex);
                    String stationLabel = "⚙️ Station in " + building.getType().name()
                            + " (-1 AP) ["
                            + building.getStationedWorkers() + "/" + building.getMaxWorkers() + "]";
                    JMenuItem stationItem = new JMenuItem(stationLabel);
                    if (!canStation) stationItem.setEnabled(false);
                    stationItem.addActionListener(ev -> {
                        mainController.getUnitController().handleStation(worker, hex);
                        repaint();
                    });
                    popup.add(stationItem);
                } else {
                    JMenuItem noBuilding = new JMenuItem("⛔ No workable facility here");
                    noBuilding.setEnabled(false);
                    popup.add(noBuilding);
                }
            }

        } else if (selectedUnit instanceof BorderExpander) {
            BorderExpander expander = (BorderExpander) selectedUnit;
            boolean canExpand = hex.isExplored() && expander.getCurrentAP() >= 1;
            JMenuItem expandItem = new JMenuItem("🗺️ Expand Border (Consume Unit)");
            if (!canExpand) expandItem.setEnabled(false);
            expandItem.addActionListener(ev -> {
                expander.expandBorder(mainController.getGameMap());
                selectedUnit = null;
                repaint();
            });
            popup.add(expandItem);
        }

        if (popup.getComponentCount() > 0) {
            popup.show(this, e.getX(), e.getY());
        }
    }

    private JMenuItem createBuildMenuItem(Builder builder, Hex hex, BuildingType type, String label) {
        String fullLabel = label + " (-" + type.getApCost() + " AP | "
                + (type.getWoodCost() > 0 ? type.getWoodCost() + "W " : "")
                + (type.getStoneCost() > 0 ? type.getStoneCost() + "S " : "")
                + (type.getIronCost() > 0 ? type.getIronCost() + "I" : "") + ")";

        JMenuItem item = new JMenuItem(fullLabel.trim());
        boolean canBuild = mainController.getBuildController().canBuild(type, hex, builder);
        if (!canBuild) item.setEnabled(false);
        item.addActionListener(ev -> {
            mainController.getBuildController().buildStructure(builder, type, hex);
            repaint();
        });
        return item;
    }

    private void selectUnitAt(Hex hex) {
        selectedUnit = null;
        for (Unit u : mainController.getGameMap().getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getQ() == hex.getQ() && u.getR() == hex.getR()) {
                selectedUnit = u;
                break;
            }
        }
    }

    private void handleMovementCommand(Hex targetHex) {
        if (selectedUnit == null) return;
        if (mainController.getUnitController().canMove(selectedUnit, targetHex)) {
            animatingUnit = selectedUnit;
            Point startPt = getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
            Point targetPt = getHexPixelCoords(targetHex.getQ(), targetHex.getR());
            animStartX = startPt.x;
            animStartY = startPt.y;
            animTargetX = targetPt.x;
            animTargetY = targetPt.y;
            animTargetQ = targetHex.getQ();
            animTargetR = targetHex.getR();
            animCost = targetHex.getTerrainType().getMovementCost();
            animProgress = 0.0;
        }
    }

    private void updateAnimation() {
        if (animatingUnit != null) {
            animProgress += 0.08;
            if (animProgress >= 1.0) {
                animProgress = 1.0;
                animatingUnit.moveTo(animTargetQ, animTargetR, animCost);
                mainController.getGameMap().updateFogOfWar();
                animatingUnit = null;
            }
        }
    }

    private Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        return new Point((int)(x * zoomFactor) + offsetX, (int)(y * zoomFactor) + offsetY);
    }

    private Polygon createHexPolygon(Point pt, int size) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle_rad = Math.PI / 180 * (60 * i - 30);
            polygon.addPoint(
                    pt.x + (int)(size * Math.cos(angle_rad)),
                    pt.y + (int)(size * Math.sin(angle_rad))
            );
        }
        return polygon;
    }

    private Hex getHexAtPixel(Point p) {
        Hex closest = null;
        double minDst = Double.MAX_VALUE;
        for (Hex hex : mainController.getGameMap().getHexes()) {
            double dst = p.distance(getHexPixelCoords(hex.getQ(), hex.getR()));
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
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        for (Hex hex : mainController.getGameMap().getHexes()) drawHexTerrain(g2d, hex);

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (hex.isInsideBorder() && hex.isExplored()) {
                drawHexBorder(g2d, hex);
            }
        }

        drawMovementHighlights(g2d);

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (hex.isExplored()) drawHexDetails(g2d, hex);
        }

        // [اصلاح حیاتی گام ۸]: پاک شدن هاردکد تان‌هال! گرافیک آن به drawBuildingIcon منتقل شد.

        for (Unit u : mainController.getGameMap().getUnits()) {
            if (u.isAlive()) drawUnit(g2d, u);
        }
    }

    private void drawMovementHighlights(Graphics2D g2d) {
        if (selectedUnit == null || animatingUnit != null) return;
        if (selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed()) return;

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (mainController.getUnitController().canMove(selectedUnit, hex)) {
                Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
                int currentSize = (int)(HEX_SIZE * zoomFactor);
                Polygon polygon = createHexPolygon(pt, currentSize);

                g2d.setColor(new Color(135, 206, 250, 70));
                g2d.fillPolygon(polygon);
                g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
                g2d.setColor(new Color(0, 255, 255, 200));
                g2d.drawPolygon(polygon);
            }
        }
    }

    private void drawHexTerrain(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int)(HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, currentSize);

        if (!hex.isExplored()) {
            g2d.setColor(new Color(20, 24, 30));
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(new Color(40, 45, 50));
            g2d.drawPolygon(polygon);
            return;
        }

        Color baseColor, topColor;
        switch (hex.getTerrainType()) {
            case FOREST:  baseColor = new Color(34, 139, 34);  topColor = new Color(50, 205, 50);  break;
            case PLAINS:  baseColor = new Color(189, 183, 107); topColor = new Color(240, 230, 140); break;
            case MOUNTAIN:baseColor = new Color(105, 105, 105); topColor = new Color(169, 169, 169); break;
            case MEADOW:  baseColor = new Color(107, 142, 35);  topColor = new Color(154, 205, 50);  break;
            default:      baseColor = Color.DARK_GRAY;          topColor = Color.GRAY;               break;
        }

        GradientPaint gp = new GradientPaint(pt.x, pt.y - currentSize, topColor, pt.x, pt.y + currentSize, baseColor);
        g2d.setPaint(gp);
        g2d.fillPolygon(polygon);

        if (hex == hoveredHex && selectedUnit == null) {
            g2d.setColor(new Color(255, 255, 255, 60));
            g2d.fillPolygon(polygon);
        }

        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(new Color(0, 0, 0, 120));
        g2d.drawPolygon(polygon);
    }

    private void drawHexBorder(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int currentSize = (int)(HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, currentSize);

        g2d.setStroke(new BasicStroke((float)(4.0 * zoomFactor)));
        g2d.setColor(new Color(255, 215, 0, 180));
        g2d.drawPolygon(polygon);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawHexDetails(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int fontSize = (int)(14 * zoomFactor);
        int rSize = (int)(18 * zoomFactor);

        if (hex.getResourceType() != ResourceType.NONE) {
            if (hex.isResourceDepleted()) {
                g2d.setColor(new Color(20, 20, 20, 200));
                g2d.fillOval(pt.x - rSize/2, pt.y - (int)(18 * zoomFactor) - rSize/2, rSize, rSize);
                g2d.setColor(new Color(220, 20, 60));
                g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
                g2d.drawLine(pt.x - rSize/4, pt.y - (int)(18 * zoomFactor) - rSize/4,
                        pt.x + rSize/4, pt.y - (int)(18 * zoomFactor) + rSize/4);
                g2d.drawLine(pt.x + rSize/4, pt.y - (int)(18 * zoomFactor) - rSize/4,
                        pt.x - rSize/4, pt.y - (int)(18 * zoomFactor) + rSize/4);
                g2d.setStroke(new BasicStroke(1f));
            } else if (fontSize > 6) {
                g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                String resStr;
                Color iconColor;
                switch (hex.getResourceType()) {
                    case WOOD:  resStr = "W"; iconColor = new Color(205, 133, 63);  break;
                    case STONE: resStr = "S"; iconColor = new Color(220, 220, 220); break;
                    case IRON:  resStr = "I"; iconColor = new Color(255, 140, 0);   break;
                    case FOOD:  resStr = "F"; iconColor = new Color(255, 215, 0);   break;
                    default:    resStr = "?"; iconColor = Color.WHITE;              break;
                }
                g2d.setColor(new Color(0, 0, 0, 180));
                g2d.fillOval(pt.x - rSize/2, pt.y - (int)(18 * zoomFactor) - rSize/2, rSize, rSize);
                g2d.setColor(iconColor);
                g2d.drawOval(pt.x - rSize/2, pt.y - (int)(18 * zoomFactor) - rSize/2, rSize, rSize);
                g2d.drawString(resStr, pt.x - fontSize/3, pt.y - (int)(18 * zoomFactor) + fontSize/3);
            }
        }

        if (hex.getBuilding() != null) {
            drawBuildingIcon(g2d, hex.getBuilding(), pt, (int)(22 * zoomFactor));
        }
    }

    private void drawBuildingIcon(Graphics2D g2d, Building b, Point pt, int size) {
        // [اصلاح حیاتی گام ۸]: رندر تخصصی TownHall
        if (b.getType() == BuildingType.TOWN_HALL) {
            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRoundRect(pt.x - (int)(18 * zoomFactor) + 2, pt.y - (int)(18 * zoomFactor) + 4,
                    (int)(36 * zoomFactor), (int)(36 * zoomFactor), 10, 10);
            g2d.setColor(new Color(255, 215, 0));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawRoundRect(pt.x - (int)(18 * zoomFactor), pt.y - (int)(18 * zoomFactor),
                    (int)(36 * zoomFactor), (int)(36 * zoomFactor), 10, 10);
            g2d.setFont(new Font("SansSerif", Font.BOLD, (int)(18 * zoomFactor)));
            g2d.drawString("TH", pt.x - (int)(12 * zoomFactor), pt.y + (int)(6 * zoomFactor));
            return; // اگر تان‌هال بود، بقیه رسم‌ها انجام نشود
        }

        int x = pt.x;
        int y = pt.y + 4;

        g2d.setColor(new Color(0, 0, 0, 120));
        g2d.fillOval(x - size/2 - 2, y + size/3 + 2, size + 4, size/2);

        if (b.isDestroyed()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x - size/2, y - size/4, size, size/2);
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawLine(x - size/2, y - size/4, x + size/2, y + size/4);
            return;
        }

        if (b.getType() == BuildingType.SETTLEMENT) {
            g2d.setColor(new Color(147, 112, 219));
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
            g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
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

        int fontSize = (int)(12 * zoomFactor);
        if (fontSize > 6) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize - 2));
            String workerText = b.getStationedWorkers() + "/" + b.getMaxWorkers();
            g2d.setColor(new Color(0, 0, 0, 200));
            g2d.fillRoundRect(x - size/2 - 4, y + size/2 + 2, size + 8, fontSize + 2, 4, 4);
            g2d.setColor(Color.WHITE);
            g2d.drawString(workerText, x - size/2 + 1, y + size/2 + fontSize);
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u) {
        boolean isStationed = (u instanceof Worker && ((Worker) u).isStationed());

        Hex unitHex = mainController.getGameMap().getHexAt(u.getQ(), u.getR());
        if (unitHex != null && !unitHex.isExplored()) return;

        int px, py;
        if (u == animatingUnit) {
            double easeOut = 1.0 - Math.pow(1.0 - animProgress, 3);
            px = (int)(animStartX + (animTargetX - animStartX) * easeOut);
            py = (int)(animStartY + (animTargetY - animStartY) * easeOut);
        } else {
            Point pt = getHexPixelCoords(u.getQ(), u.getR());
            px = pt.x;
            py = pt.y;
        }

        int baseRadius = Math.max(6, (int)(16 * zoomFactor));
        int radius = (u == selectedUnit && !isStationed) ? (int)(baseRadius * pulseScale) : baseRadius;

        String typeLetter;
        Color unitColor;
        if (u instanceof Explorer)      { unitColor = new Color(65, 105, 225);  typeLetter = "E"; }
        else if (u instanceof Builder)  { unitColor = new Color(255, 215, 0);   typeLetter = "B"; }
        else if (u instanceof Worker)   { unitColor = new Color(255, 140, 0);   typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = new Color(218, 112, 214); typeLetter = "X"; }
        else                            { unitColor = Color.GRAY;               typeLetter = "U"; }

        if (isStationed) {
            radius = Math.max(4, (int)(10 * zoomFactor));
            px += (int)(18 * zoomFactor);
            py -= (int)(8 * zoomFactor);
            g2d.setColor(new Color(255, 165, 0, 150));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawOval(px - radius - 3, py - radius - 3, (radius + 3) * 2, (radius + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillOval(px - radius + 3, py - radius + 4, radius * 2, radius * 2);

        GradientPaint up = new GradientPaint(px, py - radius, unitColor.brighter(), px, py + radius, unitColor.darker());
        g2d.setPaint(up);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);

        int fontSize = (int)(13 * zoomFactor);
        if (fontSize > 6 && !isStationed) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            String text = typeLetter + u.getCurrentAP();
            FontMetrics fm = g2d.getFontMetrics();
            int txtX = px - fm.stringWidth(text) / 2;
            int txtY = py + fm.getAscent() / 2 - 2;
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, txtX + 1, txtY + 1);
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, txtX, txtY);
        }

        if (u == selectedUnit) {
            g2d.setColor(new Color(0, 255, 255, 200));
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawOval(px - radius - 5, py - radius - 5, radius * 2 + 10, radius * 2 + 10);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}