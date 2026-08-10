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
        if (panel.isAnimating()) return;

        Unit selectedUnit = panel.getSelectedUnit();

        // 1. اولویت تعامل با ساختمان‌های ویژه (لغو انتخاب خودکار برای راحتی کلیک)
        if (clickedHex.getBuilding() != null) {
            BuildingType bType = clickedHex.getBuilding().getType();
            if (bType == BuildingType.TOWN_HALL || bType == BuildingType.TRIBE_CAMP) {

                boolean isWorkerOnHex = (selectedUnit instanceof Worker && selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR());
                boolean isMilitaryTargeting = (selectedUnit != null && selectedUnit.getAttackRange() > 0 && bType == BuildingType.TRIBE_CAMP);

                // اگر یونیت رزمی روی کمپ کلیک راست کند، قصد حمله دارد نه تعامل صلح‌آمیز!
                if (!isWorkerOnHex && !isMilitaryTargeting) {
                    panel.setSelectedUnit(null);
                    selectedUnit = null;

                    if (bType == BuildingType.TOWN_HALL) {
                        panel.showContextMenu(e.getPoint(), mainController.getTownHallMenuActions());
                    } else {
                        // اجرای هوک UI تعامل صلح‌آمیز با قبیله (باگ 01)
                        panel.onTribeInteractionTriggered(clickedHex);
                    }
                    return;
                }
            }
        }

        // 2. مدیریت منوی یونیت‌ها، حمله، یا حرکت
        if (selectedUnit != null) {
            if (selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR()) {
                panel.showContextMenu(e.getPoint(), mainController.getUnitMenuActions(selectedUnit, clickedHex));
            } else {
                // بررسی وجود دشمن در هکس هدف برای باز کردن منوی حمله (باگ 04)
                boolean hasEnemy = clickedHex.getBuilding() instanceof TribeCamp ||
                        mainController.getGameMap().getUnits().stream().anyMatch(u -> u.getQ() == clickedHex.getQ() && u.getR() == clickedHex.getR() &&
                                (u.getClass().getSimpleName().equals("Bear") || u.getClass().getSimpleName().equals("Barbarian")));

                if (hasEnemy && selectedUnit.getAttackRange() > 0) {
                    panel.showContextMenu(e.getPoint(), mainController.getUnitMenuActions(selectedUnit, clickedHex));
                } else if (mainController.canMove(selectedUnit, clickedHex)) {
                    Point startPt = panel.getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
                    Point targetPt = panel.getHexPixelCoords(clickedHex.getQ(), clickedHex.getR());
                    panel.startAnimation(selectedUnit, clickedHex, startPt.x, startPt.y, targetPt.x, targetPt.y);
                }
            }
        }
    }
}