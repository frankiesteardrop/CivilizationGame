package view;

import controller.MainController;
import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class GameInputHandler extends MouseAdapter {
    private final GamePanel panel;
    private final MainController mainController;
    private Point lastMousePosition;
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 5;

    public GameInputHandler(GamePanel panel, MainController mainController) {
        this.panel = panel;
        this.mainController = mainController;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        lastMousePosition = e.getPoint();
        isDragging = false;
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (!isDragging) {
            Hex clickedHex = panel.getHexAtPixel(e.getPoint(), mainController.getGameMap().getHexes());
            if (clickedHex == null) return;

            if (SwingUtilities.isLeftMouseButton(e)) {
                // [گام ۴]: استفاده مستقیم از Facade به جای صحبت با UnitController
                Unit selected = mainController.selectUnitAt(clickedHex);
                panel.setSelectedUnit(selected);
                panel.repaint();
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
                panel.setOffsetX(panel.getOffsetX() + e.getX() - lastMousePosition.x);
                panel.setOffsetY(panel.getOffsetY() + e.getY() - lastMousePosition.y);
                lastMousePosition = e.getPoint();
                panel.repaint();
            }
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        Hex currentHover = panel.getHexAtPixel(e.getPoint(), mainController.getGameMap().getHexes());
        if (currentHover != panel.getHoveredHex()) {
            panel.setHoveredHex(currentHover);
            panel.repaint();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int zoomIndex = panel.getZoomIndex();
        int oldZoomIndex = zoomIndex;
        if (e.getWheelRotation() < 0 && zoomIndex < GamePanel.ZOOM_LEVELS.length - 1) zoomIndex++;
        else if (e.getWheelRotation() > 0 && zoomIndex > 0) zoomIndex--;

        if (oldZoomIndex != zoomIndex) {
            double oldZoom = panel.getZoomFactor();
            panel.setZoomIndex(zoomIndex);
            double lx = (e.getX() - panel.getOffsetX()) / oldZoom;
            double ly = (e.getY() - panel.getOffsetY()) / oldZoom;
            panel.setOffsetX((int)(e.getX() - lx * panel.getZoomFactor()));
            panel.setOffsetY((int)(e.getY() - ly * panel.getZoomFactor()));
            panel.repaint();
        }
    }

    private void handleRightClick(MouseEvent e, Hex clickedHex) {
        Unit selectedUnit = panel.getSelectedUnit();

        if (selectedUnit != null) {
            if (selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR()) {
                showUnitContextMenu(e, clickedHex);
            } else if (!panel.isAnimating()) {
                if (!(selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed())) {
                    handleMovementCommand(clickedHex);
                }
            }
            return;
        }

        if (clickedHex.getBuilding() != null && clickedHex.getBuilding().getType() == BuildingType.TOWN_HALL) {
            showTownHallMenu(e);
        }
    }

    private void handleMovementCommand(Hex targetHex) {
        Unit selectedUnit = panel.getSelectedUnit();
        if (selectedUnit == null) return;

        // [گام ۴]: استفاده از Facade
        if (!mainController.canMove(selectedUnit, targetHex)) return;

        Point startPt = panel.getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
        Point targetPt = panel.getHexPixelCoords(targetHex.getQ(), targetHex.getR());

        panel.startAnimation(selectedUnit, targetHex, startPt.x, startPt.y, targetPt.x, targetPt.y);
    }

    private void showTownHallMenu(MouseEvent e) {
        JPopupMenu popup = new JPopupMenu();
        stylePopupMenu(popup);
        TownHall th = mainController.getGameMap().getTownHall();

        int whWoodCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_WOOD : GameConfig.WAREHOUSE_LVL2_WOOD;
        int whStoneCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_STONE : GameConfig.WAREHOUSE_LVL2_STONE;

        String whLabel = th.getWarehouseUpgradeLevel() >= 2
                ? "✅ Warehouse MAXED"
                : String.format("📦 Upgrade Warehouse (%dW, %dS) — Level %d", whWoodCost, whStoneCost, th.getWarehouseUpgradeLevel() + 1);

        JMenuItem whItem = new JMenuItem(whLabel);
        styleMenuItem(whItem);

        // [گام ۴]: استفاده از Facade
        if (!mainController.canAffordWarehouseUpgrade()) whItem.setEnabled(false);
        whItem.addActionListener(ev -> { mainController.handleWarehouseUpgrade(); panel.repaint(); });
        popup.add(whItem);
        popup.addSeparator();

        addTechMenuItem(popup, th.isStoneMineUnlocked(), "STONE_MINE", String.format("⛏️ Tech: Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD));
        addTechMenuItem(popup, th.isIronMineUnlocked(), "IRON_MINE", String.format("🔩 Tech: Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE));
        addTechMenuItem(popup, th.isProfessionalToolsUnlocked(), "PROF_TOOLS", String.format("🔧 Tech: Prof. Tools (%dW, %dS, %dI)", GameConfig.TECH_PROF_TOOLS_WOOD, GameConfig.TECH_PROF_TOOLS_STONE, GameConfig.TECH_PROF_TOOLS_IRON));
        addTechMenuItem(popup, th.isSettlementUnlocked(), "SETTLEMENT", String.format("🏘️ Tech: Settlement (%dW, %dS, %dI)", GameConfig.TECH_SETTLEMENT_WOOD, GameConfig.TECH_SETTLEMENT_STONE, GameConfig.TECH_SETTLEMENT_IRON));
        popup.addSeparator();

        addTrainMenuItem(popup, "WORKER", String.format("👷 Train Worker (%dF) — %d Turn", GameConfig.WORKER_FOOD_COST, GameConfig.WORKER_TURN_COST));
        addTrainMenuItem(popup, "BUILDER", String.format("🔨 Train Builder (%dF, %dW) — %d Turns", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST, GameConfig.BUILDER_TURN_COST));
        addTrainMenuItem(popup, "EXPLORER", String.format("🧭 Train Explorer (%dF, %dW) — %d Turns", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST, GameConfig.EXPLORER_TURN_COST));
        addTrainMenuItem(popup, "BORDER_EXPANDER", String.format("🗺️ Train Border Expander (%dF, %dW, %dS) — %d Turns", GameConfig.BORDER_EXPANDER_FOOD_COST, GameConfig.BORDER_EXPANDER_WOOD_COST, GameConfig.BORDER_EXPANDER_STONE_COST, GameConfig.BORDER_EXPANDER_TURN_COST));

        popup.show(panel, e.getX(), e.getY());
    }

    private void addTechMenuItem(JPopupMenu popup, boolean isUnlocked, String techKey, String label) {
        JMenuItem item = new JMenuItem(isUnlocked ? "✅ " + label : "🔬 " + label);
        styleMenuItem(item);
        if (!mainController.canUnlockTech(techKey)) item.setEnabled(false);
        item.addActionListener(ev -> { mainController.unlockTech(techKey); panel.repaint(); });
        popup.add(item);
    }

    private void addTrainMenuItem(JPopupMenu popup, String unitType, String label) {
        JMenuItem item = new JMenuItem(label);
        styleMenuItem(item);
        if (!mainController.canTrainUnit(unitType)) item.setEnabled(false);
        item.addActionListener(ev -> { mainController.trainUnit(unitType); panel.repaint(); });
        popup.add(item);
    }

    private void showUnitContextMenu(MouseEvent e, Hex hex) {
        Unit selectedUnit = panel.getSelectedUnit();
        if (selectedUnit == null) return;
        JPopupMenu popup = new JPopupMenu();
        stylePopupMenu(popup);

        if (selectedUnit instanceof Builder) buildBuilderMenu(popup, (Builder) selectedUnit, hex);
        else if (selectedUnit instanceof Worker) buildWorkerMenu(popup, (Worker) selectedUnit, hex);
        else if (selectedUnit instanceof BorderExpander) buildExpanderMenu(popup, (BorderExpander) selectedUnit);

        if (popup.getComponentCount() > 0) popup.show(panel, e.getX(), e.getY());
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
            popup.add(createBuildMenuItem(builder, hex, BuildingType.FARM, "🌾 Build Farm"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STABLE, "🐄 Build Stable"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.STONE_MINE, "⛏️ Build Stone Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.IRON_MINE, "🔩 Build Iron Mine"));
            popup.add(createBuildMenuItem(builder, hex, BuildingType.SETTLEMENT, "🏘️ Build Settlement"));
        }
    }

    private void buildWorkerMenu(JPopupMenu popup, Worker worker, Hex hex) {
        if (worker.isStationed()) {
            JMenuItem leaveItem = new JMenuItem("🚪 Leave Facility (free — AP not restored)");
            styleMenuItem(leaveItem);
            leaveItem.setEnabled(mainController.canEject(worker));
            leaveItem.addActionListener(ev -> { mainController.handleEject(worker); panel.repaint(); });
            popup.add(leaveItem);
        } else {
            Building building = hex.getBuilding();
            if (building != null && !building.isDestroyed() && building.getType() != BuildingType.TOWN_HALL) {
                boolean canStation = mainController.canStation(worker, hex);
                String label = "⚙️ Station in " + building.getType().name() + " (-" + Worker.getStationApCost() + " AP) [" + building.getStationedWorkers() + "/" + building.getMaxWorkers() + "]";
                JMenuItem stationItem = new JMenuItem(label);
                styleMenuItem(stationItem);
                if (!canStation) stationItem.setEnabled(false);
                stationItem.addActionListener(ev -> { mainController.handleStation(worker, hex); panel.repaint(); });
                popup.add(stationItem);
            } else {
                JMenuItem noBuilding = new JMenuItem("⛔ No workable facility on this hex");
                noBuilding.setEnabled(false); styleMenuItem(noBuilding); popup.add(noBuilding);
            }
        }
    }

    private void buildExpanderMenu(JPopupMenu popup, BorderExpander expander) {
        boolean canExpand = expander.canExpand(mainController.getGameMap());
        JMenuItem expandItem = new JMenuItem("🗺️ Expand Border here (-" + BorderExpander.getExpandApCost() + " AP, unit consumed)");
        styleMenuItem(expandItem);
        if (!canExpand) expandItem.setEnabled(false);

        expandItem.addActionListener(ev -> {
            if (mainController.handleExpandBorder(expander)) {
                panel.setSelectedUnit(null);
            }
            panel.repaint();
        });
        popup.add(expandItem);
    }

    private JMenuItem createBuildMenuItem(Builder builder, Hex hex, BuildingType type, String label) {
        String cost = "";
        if (type.getWoodCost() > 0) cost += type.getWoodCost() + "W ";
        if (type.getStoneCost() > 0) cost += type.getStoneCost() + "S ";
        if (type.getIronCost() > 0) cost += type.getIronCost() + "I";
        String fullLabel = label + " (-" + type.getApCost() + "AP" + (cost.isEmpty() ? "" : " | " + cost.trim()) + ")";
        JMenuItem item = new JMenuItem(fullLabel);
        styleMenuItem(item);
        if (!mainController.canBuild(type, hex, builder)) item.setEnabled(false);
        item.addActionListener(ev -> { mainController.buildStructure(builder, type, hex); panel.repaint(); });
        return item;
    }

    private void stylePopupMenu(JPopupMenu popup) {
        popup.setBackground(new Color(30, 33, 40));
        popup.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180), 1));
    }

    private void styleMenuItem(JMenuItem item) {
        item.setBackground(new Color(30, 33, 40));
        item.setForeground(Color.WHITE);
        item.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 13));
        item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
    }
}