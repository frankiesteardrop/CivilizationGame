package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * پنل اصلی رندر نقشه بازی.
 *
 * اصلاح گام ۵:
 * - بهبود کامل گرافیک زمین‌ها: تمایز بصری واضح بین هکس با منبع و بدون منبع
 * - آیکون منابع بزرگ‌تر و واضح‌تر با نماد اختصاصی هر منبع
 * - اضافه شدن texture بصری برای هر نوع زمین
 * - رنگ overlay برای نشان دادن وضعیت منبع (دارد / ندارد / تمام شده)
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

    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5;

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
    // Mouse Listeners
    // =========================================================

    private void setupMouseListeners() {
        MouseAdapter mouseAdapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                lastMousePosition = e.getPoint();
                isDragging = false;
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (!isDragging) {
                    Hex clickedHex = getHexAtPixel(e.getPoint());
                    if (clickedHex == null) return;

                    if (SwingUtilities.isLeftMouseButton(e)) {
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
                    int dx = Math.abs(e.getX() - lastMousePosition.x);
                    int dy = Math.abs(e.getY() - lastMousePosition.y);
                    if (dx > DRAG_THRESHOLD || dy > DRAG_THRESHOLD) isDragging = true;

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
            if (e.getWheelRotation() < 0 && zoomIndex < ZOOM_LEVELS.length - 1) zoomIndex++;
            else if (e.getWheelRotation() > 0 && zoomIndex > 0) zoomIndex--;

            if (oldZoomIndex != zoomIndex) {
                double oldZoom = zoomFactor;
                zoomFactor = ZOOM_LEVELS[zoomIndex];
                double lx = (e.getX() - offsetX) / oldZoom;
                double ly = (e.getY() - offsetY) / oldZoom;
                offsetX = (int)(e.getX() - lx * zoomFactor);
                offsetY = (int)(e.getY() - ly * zoomFactor);
                repaint();
            }
        });
    }

    // =========================================================
    // هندل کردن کلیک راست
    // =========================================================

    private void handleRightClick(MouseEvent e, Hex clickedHex) {
        if (clickedHex.getBuilding() != null
                && clickedHex.getBuilding().getType() == BuildingType.TOWN_HALL
                && selectedUnit == null) {
            showTownHallMenu(e);
            return;
        }
        if (selectedUnit != null
                && selectedUnit.getQ() == clickedHex.getQ()
                && selectedUnit.getR() == clickedHex.getR()) {
            showUnitContextMenu(e, clickedHex);
            return;
        }
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

        html.append("<b style='color:#00BFFF;'>")
                .append(hex.getTerrainType().name()).append("</b>");

        // نمایش وضعیت منبع
        if (hex.getResourceType() != ResourceType.NONE) {
            html.append(" — Resource: <b>").append(hex.getResourceType().name()).append("</b>");
            if (hex.isResourceDepleted()) {
                html.append(" <span style='color:#e74c3c;'>(DEPLETED)</span>");
            }
        } else {
            html.append(" <span style='color:#7f8c8d;'>(No Resource)</span>");
        }
        html.append("<br/>");

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

        for (Unit u : mainController.getGameMap().getUnits()) {
            if (!u.isAlive()) continue;
            if (u.getQ() != hex.getQ() || u.getR() != hex.getR()) continue;
            html.append("<hr style='border-color:#444;'/>");
            html.append("<b style='color:#32CD32;'>")
                    .append(u.getClass().getSimpleName()).append("</b><br/>");
            html.append("AP: <b>").append(u.getCurrentAP())
                    .append("/").append(u.getMaxAP()).append("</b>");
            if (u instanceof Builder)
                html.append("<br/>Charges: <b>").append(((Builder) u).getCharges()).append("</b>");
            if (u instanceof Worker && ((Worker) u).isStationed())
                html.append(" <span style='color:#FFA500;'>(Stationed)</span>");
            if (u instanceof BorderExpander)
                html.append("<br/>Expand Cost: <b>-")
                        .append(BorderExpander.getExpandApCost()).append(" AP</b>");
        }

        html.append("</body></html>");
        return html.toString();
    }

    // =========================================================
    // Pulse Effect
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

        addTechMenuItem(popup, th.isStoneMineUnlocked(),
                "STONE_MINE", "⛏️ Tech: Stone Mine (50W)");
        addTechMenuItem(popup, th.isIronMineUnlocked(),
                "IRON_MINE", "🔩 Tech: Iron Mine (100W, 50S)");
        addTechMenuItem(popup, th.isProfessionalToolsUnlocked(),
                "PROF_TOOLS", "🔧 Tech: Prof. Tools (100W, 100S, 50I)");
        addTechMenuItem(popup, th.isSettlementUnlocked(),
                "SETTLEMENT", "🏘️ Tech: Settlement (150W, 100S, 50I)");
        popup.addSeparator();

        addTrainMenuItem(popup, "WORKER",          "👷 Train Worker (20F) — 1 Turn");
        addTrainMenuItem(popup, "BUILDER",         "🔨 Train Builder (30F, 10W) — 2 Turns");
        addTrainMenuItem(popup, "EXPLORER",        "🧭 Train Explorer (40F, 5W) — 3 Turns");
        addTrainMenuItem(popup, "BORDER_EXPANDER", "🗺️ Train Border Expander (30F, 20W, 10S) — 3 Turns");

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

        if (selectedUnit instanceof Builder)
            buildBuilderMenu(popup, (Builder) selectedUnit, hex);
        else if (selectedUnit instanceof Worker)
            buildWorkerMenu(popup, (Worker) selectedUnit, hex);
        else if (selectedUnit instanceof BorderExpander)
            buildExpanderMenu(popup, (BorderExpander) selectedUnit);

        if (popup.getComponentCount() > 0)
            popup.show(this, e.getX(), e.getY());
    }

    private void buildBuilderMenu(JPopupMenu popup, Builder builder, Hex hex) {
        if (hex.getBuilding() != null) {
            JMenuItem item = new JMenuItem("⛔ Hex already has a building");
            item.setEnabled(false); styleMenuItem(item); popup.add(item);
        } else if (!hex.isInsideBorder()) {
            JMenuItem item = new JMenuItem("⛔ Must be inside your borders");
            item.setEnabled(false); styleMenuItem(item); popup.add(item);
        } else {
            popup.add(createBuildMenuItem(builder, hex, BuildingType.LUMBER_MILL, "🌲 Build Lumber Mill"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.FARM,        "🌾 Build Farm"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STABLE,      "🐄 Build Stable"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STONE_MINE,  "⛏️ Build Stone Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.IRON_MINE,   "🔩 Build Iron Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.SETTLEMENT,  "🏘️ Build Settlement"));
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
                noBuilding.setEnabled(false); styleMenuItem(noBuilding); popup.add(noBuilding);
            }
        }
    }

    private void buildExpanderMenu(JPopupMenu popup, BorderExpander expander) {
        boolean canExpand = expander.canExpand(mainController.getGameMap());
        JMenuItem expandItem = new JMenuItem("🗺️ Expand Border here (-"
                + BorderExpander.getExpandApCost() + " AP, unit consumed)");
        styleMenuItem(expandItem);
        if (!canExpand) expandItem.setEnabled(false);
        expandItem.addActionListener(ev -> {
            if (expander.expandBorder(mainController.getGameMap())) selectedUnit = null;
            repaint();
        });
        popup.add(expandItem);
    }

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

    // =========================================================
    // متدهای کمکی حرکت و انتخاب
    // =========================================================

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
        animStartX = startPt.x; animStartY = startPt.y;
        animTargetX = targetPt.x; animTargetY = targetPt.y;
        animTargetQ = targetHex.getQ(); animTargetR = targetHex.getR();
        animCost = targetHex.getTerrainType().getMovementCost();
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
        return new Point((int)(x * zoomFactor) + offsetX, (int)(y * zoomFactor) + offsetY);
    }

    private Polygon createHexPolygon(Point pt, int size) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            polygon.addPoint(pt.x + (int)(size * Math.cos(angle)),
                    pt.y + (int)(size * Math.sin(angle)));
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
    // Paint — ترتیب لایه‌های رندر
    // =========================================================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);

        // لایه ۱: زمین‌ها
        for (Hex hex : mainController.getGameMap().getHexes())
            drawHexTerrain(g2d, hex);

        // لایه ۲: مرزها
        for (Hex hex : mainController.getGameMap().getHexes())
            if (hex.isInsideBorder() && hex.isExplored())
                drawHexBorder(g2d, hex);

        // لایه ۳: هایلایت حرکت
        drawMovementHighlights(g2d);

        // لایه ۴: جزئیات (منابع + ساختمان‌ها)
        for (Hex hex : mainController.getGameMap().getHexes())
            if (hex.isExplored()) drawHexDetails(g2d, hex);

        // لایه ۵: یونیت‌ها
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

    // =========================================================
    // اصلاح گام ۵: رندر زمین با تمایز بصری کامل
    // =========================================================

    private void drawHexTerrain(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());
        int sz = (int)(HEX_SIZE * zoomFactor);
        Polygon polygon = createHexPolygon(pt, sz);

        // هکس ناشناخته — تاریک و مبهم
        if (!hex.isExplored()) {
            g2d.setColor(new Color(15, 18, 22));
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(new Color(35, 40, 48));
            g2d.drawPolygon(polygon);
            return;
        }

        boolean hasActiveResource = hex.getResourceType() != ResourceType.NONE
                && !hex.isResourceDepleted();
        boolean hasDepletedResource = hex.getResourceType() != ResourceType.NONE
                && hex.isResourceDepleted();

        // =========================================================
        // رنگ‌بندی بر اساس نوع زمین + وضعیت منبع
        //
        // قانون طبق داک:
        // - زمین با منبع فعال: رنگ اصلی روشن و زنده
        // - زمین بدون منبع: رنگ تیره‌تر و کم‌اشباع
        // - زمین با منبع تمام‌شده: رنگ خاکستری و فرسوده
        // =========================================================
        Color topColor, baseColor;

        switch (hex.getTerrainType()) {
            case FOREST:
                if (hasActiveResource) {
                    // جنگل با چوب: سبز تیره پر و درخشان
                    topColor  = new Color(60, 180, 60);
                    baseColor = new Color(25, 100, 25);
                } else if (hasDepletedResource) {
                    // جنگل بدون چوب: خاکستری-سبز کم‌رنگ
                    topColor  = new Color(90, 110, 80);
                    baseColor = new Color(55, 70, 50);
                } else {
                    // جنگل بدون منبع اصلی (نباید پیش بیاید اما حالت دفاعی)
                    topColor  = new Color(50, 130, 50);
                    baseColor = new Color(30, 90, 30);
                }
                break;

            case PLAINS:
                if (hasActiveResource) {
                    // دشت با حیوانات: زرد-سبز گرم و زنده
                    topColor  = new Color(255, 220, 80);
                    baseColor = new Color(200, 170, 50);
                } else {
                    // دشت خالی: بژ کم‌رنگ
                    topColor  = new Color(200, 190, 140);
                    baseColor = new Color(160, 148, 100);
                }
                break;

            case MOUNTAIN:
                if (hasActiveResource) {
                    ResourceType rt = hex.getResourceType();
                    if (rt == ResourceType.IRON) {
                        // کوهستان با آهن: خاکستری تیره با لبه‌های زنگی
                        topColor  = new Color(140, 120, 100);
                        baseColor = new Color(80,  65,  55);
                    } else {
                        // کوهستان با سنگ: خاکستری روشن
                        topColor  = new Color(190, 190, 185);
                        baseColor = new Color(120, 118, 115);
                    }
                } else if (hasDepletedResource) {
                    // کوهستان تخلیه‌شده: خاکستری یکنواخت
                    topColor  = new Color(130, 128, 125);
                    baseColor = new Color(80,  78,  75);
                } else {
                    // کوهستان بدون منبع
                    topColor  = new Color(160, 158, 155);
                    baseColor = new Color(100, 98,  95);
                }
                break;

            case MEADOW:
                if (hasActiveResource) {
                    // سبزه‌زار با غذا (گندم/برنج): سبز-زرد روشن و سرسبز
                    topColor  = new Color(180, 220, 80);
                    baseColor = new Color(120, 160, 40);
                } else {
                    // سبزه‌زار بدون غذا: سبز ملایم و کم‌شدت
                    topColor  = new Color(130, 155, 90);
                    baseColor = new Color(85,  110, 55);
                }
                break;

            default:
                topColor  = Color.GRAY;
                baseColor = Color.DARK_GRAY;
                break;
        }

        // رسم گرادیان اصلی زمین
        GradientPaint gp = new GradientPaint(
                pt.x, pt.y - sz, topColor,
                pt.x, pt.y + sz, baseColor);
        g2d.setPaint(gp);
        g2d.fillPolygon(polygon);

        // اگر منبع تمام شده، overlay خاکستری شفاف اضافه کن
        if (hasDepletedResource) {
            g2d.setColor(new Color(40, 40, 40, 100));
            g2d.fillPolygon(polygon);
        }

        // hover highlight
        if (hex == hoveredHex && selectedUnit == null) {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillPolygon(polygon);
        }

        // حاشیه هکس
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawPolygon(polygon);
        g2d.setStroke(new BasicStroke(1f));
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

    // =========================================================
    // اصلاح گام ۵: رندر جزئیات با آیکون‌های واضح‌تر
    // =========================================================

    private void drawHexDetails(Graphics2D g2d, Hex hex) {
        Point pt = getHexPixelCoords(hex.getQ(), hex.getR());

        // رندر آیکون منبع
        drawResourceIcon(g2d, hex, pt);

        // رندر آیکون ساختمان
        if (hex.getBuilding() != null) {
            drawBuildingIcon(g2d, hex.getBuilding(), pt, (int)(22 * zoomFactor));
        }
    }

    /**
     * رندر آیکون منبع روی هکس.
     *
     * اصلاح گام ۵:
     * - آیکون بزرگ‌تر و در موقعیت ثابت (گوشه بالا-چپ هکس)
     * - رنگ badge متمایز برای هر منبع
     * - نمایش واضح ضربدر قرمز برای منبع تمام‌شده
     * - زمین بدون منبع: هیچ آیکونی نمایش داده نمی‌شود
     */
    private void drawResourceIcon(Graphics2D g2d, Hex hex, Point pt) {
        ResourceType rt = hex.getResourceType();
        if (rt == ResourceType.NONE) return; // زمین بدون منبع — بدون آیکون

        int iconSize = Math.max(8, (int)(20 * zoomFactor));
        // موقعیت آیکون: گوشه بالا-چپ هکس
        int iconX = pt.x - (int)(HEX_SIZE * 0.55 * zoomFactor);
        int iconY = pt.y - (int)(HEX_SIZE * 0.70 * zoomFactor);

        if (hex.isResourceDepleted()) {
            // منبع تمام‌شده: دایره خاکستری با X قرمز
            g2d.setColor(new Color(50, 50, 50, 210));
            g2d.fillOval(iconX - iconSize/2, iconY - iconSize/2, iconSize, iconSize);
            g2d.setColor(new Color(180, 30, 30));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            int off = iconSize / 3;
            g2d.drawLine(iconX - off, iconY - off, iconX + off, iconY + off);
            g2d.drawLine(iconX + off, iconY - off, iconX - off, iconY + off);
            g2d.setStroke(new BasicStroke(1f));
            return;
        }

        // منبع فعال: badge رنگی + حرف
        Color bgColor, borderColor, textColor;
        String symbol;

        switch (rt) {
            case WOOD:
                bgColor     = new Color(90,  55,  20,  220);
                borderColor = new Color(180, 120, 60);
                textColor   = new Color(255, 200, 120);
                symbol      = "W";
                break;
            case STONE:
                bgColor     = new Color(70,  70,  75,  220);
                borderColor = new Color(180, 180, 185);
                textColor   = new Color(240, 240, 245);
                symbol      = "S";
                break;
            case IRON:
                bgColor     = new Color(80,  55,  30,  220);
                borderColor = new Color(200, 140, 60);
                textColor   = new Color(255, 180, 80);
                symbol      = "I";
                break;
            case FOOD:
                bgColor     = new Color(30,  90,  30,  220);
                borderColor = new Color(100, 200, 80);
                textColor   = new Color(180, 255, 130);
                symbol      = "F";
                break;
            default:
                return;
        }

        // رسم badge
        g2d.setColor(bgColor);
        g2d.fillOval(iconX - iconSize/2, iconY - iconSize/2, iconSize, iconSize);
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(iconX - iconSize/2, iconY - iconSize/2, iconSize, iconSize);
        g2d.setStroke(new BasicStroke(1f));

        // رسم حرف منبع
        int fontSize = Math.max(6, (int)(11 * zoomFactor));
        if (fontSize > 5) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fontSize));
            g2d.setColor(textColor);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(symbol,
                    iconX - fm.stringWidth(symbol) / 2,
                    iconY + fm.getAscent() / 2 - 1);
        }
    }

    private void drawBuildingIcon(Graphics2D g2d, Building b, Point pt, int size) {
        if (b.getType() == BuildingType.TOWN_HALL) {
            int w  = (int)(40 * zoomFactor);
            int h  = (int)(40 * zoomFactor);
            int bx = pt.x - w/2;
            int by = pt.y - h/2;

            // سایه
            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(bx + 3, by + 5, w, h, 12, 12);

            // پس‌زمینه
            GradientPaint bg = new GradientPaint(
                    bx, by, new Color(60, 50, 20),
                    bx, by + h, new Color(30, 25, 10));
            g2d.setPaint(bg);
            g2d.fillRoundRect(bx, by, w, h, 12, 12);

            // حاشیه طلایی
            g2d.setColor(new Color(255, 215, 0));
            g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
            g2d.drawRoundRect(bx, by, w, h, 12, 12);
            g2d.setStroke(new BasicStroke(1f));

            // متن TH
            int fs = (int)(16 * zoomFactor);
            if (fs > 6) {
                g2d.setFont(new Font("SansSerif", Font.BOLD, fs));
                g2d.setColor(new Color(255, 215, 0));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("TH", pt.x - fm.stringWidth("TH") / 2,
                        pt.y + fm.getAscent() / 2 - 2);
            }
            return;
        }

        int x = pt.x;
        int y = pt.y + 4;

        // سایه
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillOval(x - size/2 - 2, y + size/3 + 2, size + 4, size/2);

        if (b.isDestroyed()) {
            g2d.setColor(new Color(60, 55, 55));
            g2d.fillRect(x - size/2, y - size/4, size, size/2);
            g2d.setColor(new Color(200, 30, 30));
            g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
            g2d.drawLine(x - size/2, y - size/4, x + size/2, y + size/4);
            g2d.drawLine(x + size/2, y - size/4, x - size/2, y + size/4);
            g2d.setStroke(new BasicStroke(1f));
            return;
        }

        switch (b.getType()) {
            case SETTLEMENT: {
                // خانه با سقف شیب‌دار
                g2d.setColor(new Color(120, 85, 185));
                Polygon house = new Polygon();
                house.addPoint(x,          y - size/2);
                house.addPoint(x + size/2, y);
                house.addPoint(x + size/2, y + size/2);
                house.addPoint(x - size/2, y + size/2);
                house.addPoint(x - size/2, y);
                g2d.fillPolygon(house);
                g2d.setColor(new Color(200, 180, 240));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawPolygon(house);
                g2d.setStroke(new BasicStroke(1f));
                break;
            }
            case FARM: {
                // مزرعه با ردیف‌های کشت
                g2d.setColor(new Color(210, 175, 40));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(110, 75, 20));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                // خطوط ردیف
                for (int i = -1; i <= 1; i++) {
                    int lx = x + i * (size / 3);
                    g2d.drawLine(lx, y - size/4, lx, y + size/4);
                }
                g2d.setStroke(new BasicStroke(1f));
                break;
            }
            case STABLE: {
                // اصطبل با درب
                g2d.setColor(new Color(140, 90, 35));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(190, 150, 80));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                // درب
                g2d.setColor(new Color(80, 50, 20));
                g2d.fillRect(x - size/6, y, size/3, size/4);
                g2d.setStroke(new BasicStroke(1f));
                break;
            }
            case LUMBER_MILL: {
                // اره + تنه درخت
                g2d.setColor(new Color(110, 65, 20));
                g2d.fillRect(x - size/2, y, size, size/2);
                // چرخ اره
                g2d.setColor(new Color(190, 185, 175));
                g2d.fillOval(x - size/4, y - size/3, size/2, size/2);
                g2d.setColor(new Color(80, 75, 70));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawOval(x - size/4, y - size/3, size/2, size/2);
                // محور
                g2d.drawLine(x, y - size/3, x, y + size/6);
                g2d.setStroke(new BasicStroke(1f));
                break;
            }
            case STONE_MINE: {
                // مثلث خاکستری با دهانه
                g2d.setColor(new Color(130, 125, 120));
                Polygon mine = new Polygon();
                mine.addPoint(x,          y - size/2);
                mine.addPoint(x + size/2, y + size/2);
                mine.addPoint(x - size/2, y + size/2);
                g2d.fillPolygon(mine);
                g2d.setColor(new Color(30, 28, 25));
                g2d.fillArc(x - size/5, y + size/6, size/2 - size/10, size/3, 0, 180);
                break;
            }
            case IRON_MINE: {
                // مثلث قهوه‌ای-نارنجی با دهانه
                g2d.setColor(new Color(120, 90, 60));
                Polygon mine = new Polygon();
                mine.addPoint(x,          y - size/2);
                mine.addPoint(x + size/2, y + size/2);
                mine.addPoint(x - size/2, y + size/2);
                g2d.fillPolygon(mine);
                g2d.setColor(new Color(200, 130, 50));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawPolygon(mine);
                g2d.setColor(new Color(30, 28, 25));
                g2d.setStroke(new BasicStroke(1f));
                g2d.fillArc(x - size/5, y + size/6, size/2 - size/10, size/3, 0, 180);
                break;
            }
            default:
                break;
        }

        // نمایش تعداد کارگران
        int fs = (int)(11 * zoomFactor);
        if (fs > 5) {
            g2d.setFont(new Font("SansSerif", Font.BOLD, fs));
            String wt = b.getStationedWorkers() + "/" + b.getMaxWorkers();
            FontMetrics fm = g2d.getFontMetrics();
            int labelW = fm.stringWidth(wt) + 6;
            int labelX = x - labelW / 2;
            int labelY = y + size/2 + 3;
            g2d.setColor(new Color(0, 0, 0, 190));
            g2d.fillRoundRect(labelX, labelY, labelW, fs + 4, 4, 4);
            g2d.setColor(Color.WHITE);
            g2d.drawString(wt, labelX + 3, labelY + fs);
        }
    }

    // =========================================================
    // رندر یونیت‌ها
    // =========================================================

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
        if      (u instanceof Explorer)       { unitColor = new Color(65,  105, 225); typeLetter = "E"; }
        else if (u instanceof Builder)        { unitColor = new Color(255, 215, 0);   typeLetter = "B"; }
        else if (u instanceof Worker)         { unitColor = new Color(255, 140, 0);   typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = new Color(218, 112, 214); typeLetter = "X"; }
        else                                  { unitColor = Color.GRAY;               typeLetter = "U"; }

        if (isStationed) {
            radius = Math.max(4, (int)(10 * zoomFactor));
            px += (int)(18 * zoomFactor);
            py -= (int)(8  * zoomFactor);
            g2d.setColor(new Color(255, 165, 0, 150));
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawOval(px - radius - 3, py - radius - 3,
                    (radius + 3) * 2, (radius + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        // سایه
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillOval(px - radius + 3, py - radius + 4, radius * 2, radius * 2);

        // بدنه با گرادیان
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

        // متن نوع + AP
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