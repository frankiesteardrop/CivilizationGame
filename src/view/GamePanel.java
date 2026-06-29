package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * پنل اصلی رندر نقشه بازی.
 *
 * اصلاح گام ۴:
 * - تفکیک Pan از Select: کلیک ساده = انتخاب، drag = حرکت نقشه
 * - رفع باگ gamePanel.repaint() داخل showUnitContextMenu
 */
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

    // اصلاح گام ۴: تشخیص drag از click
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5; // پیکسل — زیر این مقدار = click، بیشتر = drag

    private final Timer animationTimer;

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
        setBackground(new Color(15, 18, 22));
        setFocusable(true);
        setToolTipText("");
        ToolTipManager.sharedInstance().setInitialDelay(300);
        ToolTipManager.sharedInstance().setDismissDelay(8000);

        animationTimer = new Timer(16, e -> {
            updateAnimation();
            updatePulseEffect();
            repaint();
        });
        animationTimer.start();

        setupMouseListeners();
        setupMouseWheelListener();
    }

    // =========================================================
    // راه‌اندازی Mouse Listeners — تفکیک کامل Pan از Select
    // =========================================================

    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {

            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                isDragging = false; // ریست flag در شروع هر کلیک
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // اصلاح گام ۴: فقط اگر drag نبود (یعنی یک کلیک ساده بود) عمل کن
                if (!isDragging) {
                    Hex clickedHex = getHexAtPixel(e.getPoint());
                    if (clickedHex == null) return;

                    if (SwingUtilities.isLeftMouseButton(e)) {
                        // کلیک چپ ساده = انتخاب یونیت
                        selectUnitAt(clickedHex);
                        repaint();

                    } else if (SwingUtilities.isRightMouseButton(e)) {
                        handleRightClick(e, clickedHex);
                    }
                }
                isDragging = false;
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // بررسی اینکه آیا واقعاً drag شده یا فقط لرزش ماوس است
                    int dx = Math.abs(e.getX() - lastMousePosition.x);
                    int dy = Math.abs(e.getY() - lastMousePosition.y);
                    if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        offsetX += e.getX() - lastMousePosition.x;
                        offsetY += e.getY() - lastMousePosition.y;
                        lastMousePosition = e.getPoint();
                        repaint();
                    }
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
    }

    private void setupMouseWheelListener() {
        addMouseWheelListener(e -> {
            int oldZoomIndex = zoomIndex;

            if (e.getWheelRotation() < 0 && zoomIndex < ZOOM_LEVELS.length - 1) {
                zoomIndex++;
            } else if (e.getWheelRotation() > 0 && zoomIndex > 0) {
                zoomIndex--;
            }

            if (oldZoomIndex != zoomIndex) {
                double oldZoomFactor = zoomFactor;
                zoomFactor = ZOOM_LEVELS[zoomIndex];

                // زوم به مرکز ماوس (Affine Transformation)
                double logicalX = (e.getX() - offsetX) / oldZoomFactor;
                double logicalY = (e.getY() - offsetY) / oldZoomFactor;
                offsetX = (int)(e.getX() - (logicalX * zoomFactor));
                offsetY = (int)(e.getY() - (logicalY * zoomFactor));

                repaint();
            }
        });
    }

    // =========================================================
    // هندل کردن کلیک راست
    // =========================================================

    private void handleRightClick(MouseEvent e, Hex clickedHex) {
        // اگر روی TownHall کلیک شد و یونیتی انتخاب نشده
        if (clickedHex.getBuilding() != null
                && clickedHex.getBuilding().getType() == BuildingType.TOWN_HALL
                && selectedUnit == null) {
            showTownHallMenu(e);
            return;
        }

        // اگر یونیتی انتخاب شده و روی هکس همان یونیت کلیک شد = منوی یونیت
        if (selectedUnit != null
                && selectedUnit.getQ() == clickedHex.getQ()
                && selectedUnit.getR() == clickedHex.getR()) {
            showUnitContextMenu(e, clickedHex);
            return;
        }

        // اگر یونیتی انتخاب شده و روی هکس دیگری کلیک شد = دستور حرکت
        if (selectedUnit != null && animatingUnit == null) {
            if (!(selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed())) {
                handleMovementCommand(clickedHex);
            }
        }
    }

    // =========================================================
    // Tooltip
    // =========================================================

    @Override
    public String getToolTipText(MouseEvent e) {
        Hex hex = getHexAtPixel(e.getPoint());
        if (hex == null || !hex.isExplored()) return null;

        StringBuilder html = new StringBuilder(
                "<html><body style='background-color:#1e2229; color:white; "
                        + "padding:6px; font-family:Segoe UI; font-size:12px;'>");

        // نوع زمین
        html.append("<b style='color:#00BFFF;'>")
                .append(hex.getTerrainType().name())
                .append("</b><br/>");

        // منبع هکس
        if (hex.getResourceType() != ResourceType.NONE) {
            html.append("Resource: <b>").append(hex.getResourceType().name()).append("</b>");
            if (hex.isResourceDepleted()) {
                html.append(" <span style='color:#e74c3c;'>(DEPLETED)</span>");
            }
            html.append("<br/>");
        }

        // ساختمان
        Building b = hex.getBuilding();
        if (b != null) {
            html.append("<hr style='border-color:#444;'/>");
            html.append("<b style='color:#FFD700;'>").append(b.getType().name()).append("</b><br/>");
            if (b.isDestroyed()) {
                html.append("<span style='color:#e74c3c;'>⚠ DESTROYED</span>");
            } else if (b.getType() != BuildingType.TOWN_HALL) {
                html.append("Workers: ").append(b.getStationedWorkers())
                        .append("/").append(b.getMaxWorkers()).append("<br/>");
                html.append("Production: <b>+").append(b.calculateProduction())
                        .append("</b>/Turn<br/>");
                html.append("Upkeep: -").append(b.getUpkeepAmount())
                        .append(" ").append(b.getUpkeepResource().name()).append("/Turn");
            }
        }

        // یونیت‌های روی این هکس
        for (Unit u : mainController.getGameMap().getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getQ() != hex.getQ() || u.getR() != hex.getR()) continue;

            html.append("<hr style='border-color:#444;'/>");
            html.append("<b style='color:#32CD32;'>")
                    .append(u.getClass().getSimpleName()).append("</b><br/>");
            html.append("AP: <b>").append(u.getCurrentAP())
                    .append("/").append(u.getMaxAP()).append("</b><br/>");

            if (u instanceof Builder) {
                html.append("Charges: <b>").append(((Builder) u).getCharges()).append("</b>");
            }
            if (u instanceof Worker && ((Worker) u).isStationed()) {
                html.append("<span style='color:#FFA500;'> (Stationed)</span>");
            }
            if (u instanceof BorderExpander) {
                html.append("Expand Cost: <b>-")
                        .append(BorderExpander.getExpandApCost()).append(" AP</b>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    // =========================================================
    // Pulse Effect برای یونیت انتخاب‌شده
    // =========================================================

    private void updatePulseEffect() {
        if (pulseGrowing) {
            pulseScale += 0.015;
            if (pulseScale >= 1.25) pulseGrowing = false;
        } else {
            pulseScale -= 0.015;
            if (pulseScale <= 1.0) pulseGrowing = true;
        }
    }

    // =========================================================
    // منوی Town Hall
    // =========================================================

    private void showTownHallMenu(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        stylePopupMenu(popup);
        TownHall th = mainController.getGameMap().getTownHall();

        // ارتقای انبار
        String whLabel = th.getWarehouseUpgradeLevel() >= 2
                ? "✅ Warehouse MAXED"
                : "📦 Upgrade Warehouse (100W, 50S) — Level " + (th.getWarehouseUpgradeLevel() + 1);
        JMenuItem whItem = new JMenuItem(whLabel);
        styleMenuItem(whItem);
        if (!mainController.getUpgradeController().canAffordWarehouseUpgrade())
            whItem.setEnabled(false);
        whItem.addActionListener(ev -> {
            mainController.getUpgradeController().handleWarehouseUpgrade();
            repaint();
        });
        popup.add(whItem);
        popup.addSeparator();

        // تکنولوژی‌ها
        addTechMenuItem(popup, th.isStoneMineUnlocked(),
                "STONE_MINE", "⛏️ Tech: Stone Mine (50W)");
        addTechMenuItem(popup, th.isIronMineUnlocked(),
                "IRON_MINE", "🔩 Tech: Iron Mine (100W, 50S) — Req: Stone Mine");
        addTechMenuItem(popup, th.isProfessionalToolsUnlocked(),
                "PROF_TOOLS", "🔧 Tech: Prof. Tools (100W, 100S, 50I)");
        addTechMenuItem(popup, th.isSettlementUnlocked(),
                "SETTLEMENT", "🏘️ Tech: Settlement (150W, 100S, 50I) — Req: Iron Mine");
        popup.addSeparator();

        // تولید یونیت
        addTrainMenuItem(popup, "WORKER",         "👷 Train Worker (20F) — 1 Turn");
        addTrainMenuItem(popup, "BUILDER",        "🔨 Train Builder (30F, 10W) — 2 Turns");
        addTrainMenuItem(popup, "EXPLORER",       "🧭 Train Explorer (40F, 5W) — 3 Turns");
        addTrainMenuItem(popup, "BORDER_EXPANDER","🗺️ Train Border Expander (30F, 20W, 10S) — 3 Turns");

        popup.show(this, e.getX(), e.getY());
    }

    private void addTechMenuItem(JPopupMenu popup, boolean isUnlocked,
                                 String techKey, String label) {
        JMenuItem item = new JMenuItem(isUnlocked ? "✅ " + label : "🔬 " + label);
        styleMenuItem(item);
        if (!mainController.getUpgradeController().canUnlockTech(techKey))
            item.setEnabled(false);
        item.addActionListener(ev -> {
            mainController.getUpgradeController().unlockTech(techKey);
            repaint();
        });
        popup.add(item);
    }

    private void addTrainMenuItem(JPopupMenu popup, String unitType, String label) {
        JMenuItem item = new JMenuItem(label);
        styleMenuItem(item);
        if (!mainController.getUpgradeController().canTrainUnit(unitType))
            item.setEnabled(false);
        item.addActionListener(ev -> {
            mainController.getUpgradeController().trainUnit(unitType);
            repaint();
        });
        popup.add(item);
    }

    // =========================================================
    // منوی یونیت
    // =========================================================

    private void showUnitContextMenu(MouseEvent e, Hex hex) {
        if (selectedUnit == null) return;

        JPopupMenu popup = new JPopupMenu();
        stylePopupMenu(popup);

        if (selectedUnit instanceof Builder) {
            buildBuilderMenu(popup, (Builder) selectedUnit, hex);

        } else if (selectedUnit instanceof Worker) {
            buildWorkerMenu(popup, (Worker) selectedUnit, hex);

        } else if (selectedUnit instanceof BorderExpander) {
            buildExpanderMenu(popup, (BorderExpander) selectedUnit);
        }

        if (popup.getComponentCount() > 0) {
            popup.show(this, e.getX(), e.getY());
        }
    }

    private void buildBuilderMenu(JPopupMenu popup, Builder builder, Hex hex) {
        if (hex.getBuilding() != null) {
            JMenuItem item = new JMenuItem("⛔ Hex already has a building");
            item.setEnabled(false);
            styleMenuItem(item);
            popup.add(item);
        } else if (!hex.isInsideBorder()) {
            JMenuItem item = new JMenuItem("⛔ Must be inside your borders");
            item.setEnabled(false);
            styleMenuItem(item);
            popup.add(item);
        } else {
            popup.add(createBuildMenuItem(builder, hex, BuildingType.LUMBER_MILL,
                    "🌲 Build Lumber Mill"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.FARM,
                    "🌾 Build Farm"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STABLE,
                    "🐄 Build Stable"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STONE_MINE,
                    "⛏️ Build Stone Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.IRON_MINE,
                    "🔩 Build Iron Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.SETTLEMENT,
                    "🏘️ Build Settlement"));
        }
    }

    private void buildWorkerMenu(JPopupMenu popup, Worker worker, Hex hex) {
        if (worker.isStationed()) {
            JMenuItem leaveItem = new JMenuItem("🚪 Leave Facility (free — AP not restored)");
            styleMenuItem(leaveItem);
            leaveItem.setEnabled(mainController.getUnitController().canEject(worker));
            leaveItem.addActionListener(ev -> {
                mainController.getUnitController().handleEject(worker);
                repaint();
            });
            popup.add(leaveItem);

        } else {
            Building building = hex.getBuilding();
            if (building != null && !building.isDestroyed()
                    && building.getType() != BuildingType.TOWN_HALL) {

                boolean canStation = mainController.getUnitController().canStation(worker, hex);
                String label = "⚙️ Station in " + building.getType().name()
                        + " (-" + Worker.getStationApCost() + " AP) ["
                        + building.getStationedWorkers() + "/" + building.getMaxWorkers() + "]";

                JMenuItem stationItem = new JMenuItem(label);
                styleMenuItem(stationItem);
                if (!canStation) stationItem.setEnabled(false);
                stationItem.addActionListener(ev -> {
                    mainController.getUnitController().handleStation(worker, hex);
                    repaint();
                });
                popup.add(stationItem);

            } else {
                JMenuItem noBuilding = new JMenuItem("⛔ No workable facility on this hex");
                noBuilding.setEnabled(false);
                styleMenuItem(noBuilding);
                popup.add(noBuilding);
            }
        }
    }

    private void buildExpanderMenu(JPopupMenu popup, BorderExpander expander) {
        boolean canExpand = expander.canExpand(mainController.getGameMap());
        String label = "🗺️ Expand Border here (-" + BorderExpander.getExpandApCost()
                + " AP, unit consumed)";

        JMenuItem expandItem = new JMenuItem(label);
        styleMenuItem(expandItem);
        if (!canExpand) expandItem.setEnabled(false);

        expandItem.addActionListener(ev -> {
            boolean success = expander.expandBorder(mainController.getGameMap());
            if (success) selectedUnit = null;
            repaint(); // اصلاح گام ۴: به جای gamePanel.repaint() از repaint() استفاده می‌شود
        });
        popup.add(expandItem);
    }

    // =========================================================
    // متدهای کمکی
    // =========================================================

    private JMenuItem createBuildMenuItem(Builder builder, Hex hex,
                                          BuildingType type, String label) {
        String cost = "";
        if (type.getWoodCost()  > 0) cost += type.getWoodCost()  + "W ";
        if (type.getStoneCost() > 0) cost += type.getStoneCost() + "S ";
        if (type.getIronCost()  > 0) cost += type.getIronCost()  + "I";

        String fullLabel = label + " (-" + type.getApCost() + "AP"
                + (cost.isEmpty() ? "" : " | " + cost.trim()) + ")";

        JMenuItem item = new JMenuItem(fullLabel);
        styleMenuItem(item);
        if (!mainController.getBuildController().canBuild(type, hex, builder))
            item.setEnabled(false);

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
        if (!mainController.getUnitController().canMove(selectedUnit, targetHex)) return;

        animatingUnit = selectedUnit;
        Point startPt  = getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
        Point targetPt = getHexPixelCoords(targetHex.getQ(), targetHex.getR());
        animStartX  = startPt.x;
        animStartY  = startPt.y;
        animTargetX = targetPt.x;
        animTargetY = targetPt.y;
        animTargetQ = targetHex.getQ();
        animTargetR = targetHex.getR();
        animCost    = targetHex.getTerrainType().getMovementCost();
        animProgress = 0.0;
    }

    private void updateAnimation() {
        if (animatingUnit == null) return;
        animProgress += 0.08;
        if (animProgress >= 1.0) {
            animProgress = 1.0;
            animatingUnit.moveTo(animTargetQ, animTargetR, animCost);
            mainController.getGameMap().updateFogOfWar();
            animatingUnit = null;
        }
    }

    private Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        return new Point(
                (int)(x * zoomFactor) + offsetX,
                (int)(y * zoomFactor) + offsetY
        );
    }

    private Polygon createHexPolygon(Point pt, int size) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            polygon.addPoint(
                    pt.x + (int)(size * Math.cos(angle)),
                    pt.y + (int)(size * Math.sin(angle))
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

    // =========================================================
    // استایل‌دهی UI
    // =========================================================

    private void stylePopupMenu(JPopupMenu popup) {
        popup.setBackground(new Color(30, 33, 40));
        popup.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180), 1));
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(new Color(30, 33, 40));
        item.setForeground(Color.WHITE);
        item.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }

    // =========================================================
    // Paint
    // =========================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY);

        for (Hex hex : mainController.getGameMap().getHexes())
            drawHexTerrain(g2d, hex);

        for (Hex hex : mainController.getGameMap().getHexes())
            if (hex.isInsideBorder() && hex.isExplored())
                drawHexBorder(g2d, hex);

        drawMovementHighlights(g2d);

        for (Hex hex : mainController.getGameMap().getHexes())
            if (hex.isExplored()) drawHexDetails(g2d, hex);

        for (Unit u : mainController.getGameMap().getUnits())
            if (u.isAlive()) drawUnit(g2d, u);
    }

    private void drawMovementHighlights(Graphics2D g2d) {
        if (selectedUnit == null || animatingUnit != null) return;
        if (selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed()) return;

        for (Hex hex : mainController.getGameMap().getHexes()) {
            if (!mainController.getUnitController().canMove(selectedUnit, hex)) continue;

            Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
            int sz = (int)(HEX_SIZE * zoomFactor);
            Polygon polygon = createHexPolygon(pt, sz);

            g2d.setColor(new Color(135, 206, 250, 70));
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
            g2d.setColor(new Color(0, 255, 255, 200));
            g2d.drawPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
        }
    }

    private void drawHexTerrain(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int sz = (int)(HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, sz);

        if (!hex.isExplored()) {
            g2d.setColor(new Color(20, 24, 30));
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(new Color(40, 45, 50));
            g2d.drawPolygon(polygon);
            return;
        }

        // رنگ پایه بر اساس نوع زمین
        Color baseColor, topColor;
        switch (hex.getTerrainType()) {
            case FOREST:   baseColor = new Color(34, 139, 34);   topColor = new Color(50, 205, 50);   break;
            case PLAINS:   baseColor = new Color(189, 183, 107); topColor = new Color(240, 230, 140); break;
            case MOUNTAIN: baseColor = new Color(105, 105, 105); topColor = new Color(169, 169, 169); break;
            case MEADOW:   baseColor = new Color(107, 142, 35);  topColor = new Color(154, 205, 50);  break;
            default:       baseColor = Color.DARK_GRAY;          topColor = Color.GRAY;               break;
        }

        // اصلاح گام ۴ (بخش گرافیک): هکس‌های دارای منبع کمی روشن‌تر نمایش داده می‌شوند
        if (hex.hasResource(hex.getResourceType()) && !hex.isResourceDepleted()) {
            topColor = topColor.brighter();
        }

        GradientPaint gp = new GradientPaint(
                pt.x, pt.y - sz, topColor,
                pt.x, pt.y + sz, baseColor);
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
        int sz = (int)(HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, sz);

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
                // نمایش X قرمز برای منبع تمام‌شده
                g2d.setColor(new Color(20, 20, 20, 200));
                g2d.fillOval(pt.x - rSize/2, pt.y - rSize - rSize/2, rSize, rSize);
                g2d.setColor(new Color(220, 20, 60));
                g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
                int cx = pt.x; int cy = pt.y - rSize;
                g2d.drawLine(cx - rSize/4, cy - rSize/4, cx + rSize/4, cy + rSize/4);
                g2d.drawLine(cx + rSize/4, cy - rSize/4, cx - rSize/4, cy + rSize/4);
                g2d.setStroke(new BasicStroke(1f));
            } else if (fontSize > 6) {
                // نمایش حرف منبع
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
                int oy = (int)(18 * zoomFactor);
                g2d.setColor(new Color(0, 0, 0, 180));
                g2d.fillOval(pt.x - rSize/2, pt.y - oy - rSize/2, rSize, rSize);
                g2d.setColor(iconColor);
                g2d.drawOval(pt.x - rSize/2, pt.y - oy - rSize/2, rSize, rSize);
                g2d.drawString(resStr, pt.x - fontSize/3, pt.y - oy + fontSize/3);
            }
        }

        if (hex.getBuilding() != null) {
            drawBuildingIcon(g2d, hex.getBuilding(), pt, (int)(22 * zoomFactor));
        }
    }

    private void drawBuildingIcon(Graphics2D g2d, Building b, Point pt, int size) {
        if (b.getType() == BuildingType.TOWN_HALL) {
            int w = (int)(36 * zoomFactor);
            int h = (int)(36 * zoomFactor);
            int bx = pt.x - w/2;
            int by = pt.y - h/2;

            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillRoundRect(bx + 2, by + 4, w, h, 10, 10);
            g2d.setColor(new Color(255, 215, 0));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawRoundRect(bx, by, w, h, 10, 10);
            g2d.setFont(new Font("SansSerif", Font.BOLD, (int)(16 * zoomFactor)));
            g2d.drawString("TH", pt.x - (int)(10 * zoomFactor),
                    pt.y + (int)(6 * zoomFactor));
            g2d.setStroke(new BasicStroke(1f));
            return;
        }

        int x = pt.x;
        int y = pt.y + 4;

        // سایه
        g2d.setColor(new Color(0, 0, 0, 120));
        g2d.fillOval(x - size/2 - 2, y + size/3 + 2, size + 4, size/2);

        if (b.isDestroyed()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.fillRect(x - size/2, y - size/4, size, size/2);
            g2d.setColor(Color.RED);
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawLine(x - size/2, y - size/4, x + size/2, y + size/4);
            g2d.setStroke(new BasicStroke(1f));
            return;
        }

        switch (b.getType()) {
            case SETTLEMENT:
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
                break;

            case FARM:
                g2d.setColor(new Color(255, 215, 0));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(139, 69, 19));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                g2d.drawLine(x - size/4, y - size/4, x - size/4, y + size/4);
                g2d.drawLine(x + size/4, y - size/4, x + size/4, y + size/4);
                g2d.setStroke(new BasicStroke(1f));
                break;

            case STABLE:
                g2d.setColor(new Color(160, 100, 40));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(200, 160, 80));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                g2d.setStroke(new BasicStroke(1f));
                break;

            case LUMBER_MILL:
                g2d.setColor(new Color(139, 69, 19));
                g2d.fillRect(x - size/2, y, size, size/2);
                g2d.setColor(Color.LIGHT_GRAY);
                g2d.fillOval(x - size/4, y - size/4, size/2, size/2);
                g2d.setColor(Color.BLACK);
                g2d.drawOval(x - size/4, y - size/4, size/2, size/2);
                break;

            default: // معادن
                g2d.setColor(Color.DARK_GRAY);
                Polygon mine = new Polygon();
                mine.addPoint(x, y - size/2);
                mine.addPoint(x + size/2, y + size/2);
                mine.addPoint(x - size/2, y + size/2);
                g2d.fillPolygon(mine);
                g2d.setColor(Color.BLACK);
                g2d.fillArc(x - size/4, y, size/2, size, 0, 180);
                break;
        }

        // نمایش تعداد کارگران
        int fs = (int)(12 * zoomFactor);
        if (fs > 6) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fs - 2));
            String wt = b.getStationedWorkers() + "/" + b.getMaxWorkers();
            g2d.setColor(new Color(0, 0, 0, 200));
            g2d.fillRoundRect(x - size/2 - 4, y + size/2 + 2, size + 8, fs + 2, 4, 4);
            g2d.setColor(Color.WHITE);
            g2d.drawString(wt, x - size/2 + 1, y + size/2 + fs);
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
            px = pt.x; py = pt.y;
        }

        int baseRadius = Math.max(6, (int)(16 * zoomFactor));
        int radius = (u == selectedUnit && !isStationed)
                ? (int)(baseRadius * pulseScale) : baseRadius;

        String typeLetter;
        Color unitColor;
        if      (u instanceof Explorer)       { unitColor = new Color(65, 105, 225);  typeLetter = "E"; }
        else if (u instanceof Builder)        { unitColor = new Color(255, 215, 0);   typeLetter = "B"; }
        else if (u instanceof Worker)         { unitColor = new Color(255, 140, 0);   typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = new Color(218, 112, 214); typeLetter = "X"; }
        else                                  { unitColor = Color.GRAY;               typeLetter = "U"; }

        if (isStationed) {
            radius = Math.max(4, (int)(10 * zoomFactor));
            px += (int)(18 * zoomFactor);
            py -= (int)(8 * zoomFactor);
            g2d.setColor(new Color(255, 165, 0, 150));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawOval(px - radius - 3, py - radius - 3,
                    (radius + 3) * 2, (radius + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        // سایه
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillOval(px - radius + 3, py - radius + 4, radius * 2, radius * 2);

        // بدنه
        GradientPaint gp = new GradientPaint(
                px, py - radius, unitColor.brighter(),
                px, py + radius, unitColor.darker());
        g2d.setPaint(gp);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        // حاشیه
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setStroke(new BasicStroke(1f));

        // متن AP
        int fs = (int)(13 * zoomFactor);
        if (fs > 6 && !isStationed) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fs));
            String text = typeLetter + u.getCurrentAP();
            FontMetrics fm = g2d.getFontMetrics();
            int tx = px - fm.stringWidth(text) / 2;
            int ty = py + fm.getAscent() / 2 - 2;
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, tx + 1, ty + 1);
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }

        // حلقه انتخاب
        if (u == selectedUnit) {
            g2d.setColor(new Color(0, 255, 255, 200));
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawOval(px - radius - 5, py - radius - 5,
                    radius * 2 + 10, radius * 2 + 10);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}