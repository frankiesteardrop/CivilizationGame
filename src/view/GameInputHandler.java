package view;

import controller.MainController;
import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

public class GameInputHandler extends MouseAdapter {

    private final GamePanel      panel;
    private final MainController mainController;
    private Point   lastMousePosition;
    private boolean isDragging    = false;
    private static final int DRAG_THRESHOLD = 5;

    public GameInputHandler(GamePanel panel, MainController mainController) {
        this.panel          = panel;
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
            Hex clickedHex = panel.getHexAtPixel(e.getPoint(),
                    mainController.getGameMap().getHexes());
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
        Hex current = panel.getHexAtPixel(e.getPoint(),
                mainController.getGameMap().getHexes());
        if (current != panel.getHoveredHex()) {
            panel.setHoveredHex(current);
            panel.repaint();
        }
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        int zoomIndex    = panel.getZoomIndex();
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

        // ─── ۱. ساختمان‌های ویژه ─────────────────────────────────────────────
        if (clickedHex.getBuilding() != null && !clickedHex.getBuilding().isDestroyed()) {
            BuildingType bType = clickedHex.getBuilding().getType();

            if (bType == BuildingType.TOWN_HALL) {
                boolean isMilitarySelected = selectedUnit != null
                        && selectedUnit.getAttackRange() > 0;
                if (!isMilitarySelected) {
                    panel.setSelectedUnit(null);
                    panel.showContextMenu(e.getPoint(),
                            mainController.getTownHallMenuActions());
                    return;
                }
            }

            if (bType == BuildingType.TRIBE_CAMP) {
                TribeCamp camp    = (TribeCamp) clickedHex.getBuilding();
                boolean isEnemy   = camp.getTribe().getRelationship() <= -50;

                if (selectedUnit != null && selectedUnit.getAttackRange() > 0 && isEnemy) {
                    panel.showContextMenu(e.getPoint(),
                            mainController.getUnitMenuActions(selectedUnit, clickedHex));
                    return;
                }
                panel.setSelectedUnit(null);
                panel.onTribeInteractionTriggered(clickedHex);
                return;
            }
        }

        // ─── ۲. یونیت انتخاب‌شده ────────────────────────────────────────────
        if (selectedUnit != null) {
            boolean isSameHex = (selectedUnit.getQ() == clickedHex.getQ()
                    && selectedUnit.getR() == clickedHex.getR());

            if (isSameHex) {
                panel.showContextMenu(e.getPoint(),
                        mainController.getUnitMenuActions(selectedUnit, clickedHex));
                return;
            }

            // ─── تشخیص دشمن روی hex هدف ──────────────────────────────────────
            // F-28: استفاده از getType() به جای getSimpleName()
            boolean hasEnemyUnit = mainController.getGameMap().getUnits().stream()
                    .anyMatch(u -> u.isAlive()
                            && u.getQ() == clickedHex.getQ()
                            && u.getR() == clickedHex.getR()
                            && u.getType() == UnitType.BEAR);

            boolean hasEnemyBuilding = (clickedHex.getBuilding() instanceof TribeCamp)
                    && ((TribeCamp) clickedHex.getBuilding())
                    .getTribe().getRelationship() <= -50;

            boolean hasEnemy = hasEnemyUnit || hasEnemyBuilding;

            // ─── محاسبه فاصله برای تشخیص Capture ────────────────────────────
            int dist = mainController.getGameMap().getHexDistance(
                    selectedUnit.getQ(), selectedUnit.getR(),
                    clickedHex.getQ(), clickedHex.getR());

            // F-22: یونیت نظامی مجاور hex خالی و تصرف‌نشده → نمایش منوی Capture
            boolean hexIsCapturableByMilitary = selectedUnit.getAttackRange() > 0
                    && !hasEnemy
                    && !clickedHex.isInsideBorder()
                    && dist == 1;

            if (hasEnemy && selectedUnit.getAttackRange() > 0) {
                // حمله به دشمن
                panel.showContextMenu(e.getPoint(),
                        mainController.getUnitMenuActions(selectedUnit, clickedHex));

            } else if (hexIsCapturableByMilitary) {
                // F-22: نمایش منوی Capture برای hex خالی
                panel.showContextMenu(e.getPoint(),
                        mainController.getUnitMenuActions(selectedUnit, clickedHex));

            } else if (mainController.canMove(selectedUnit, clickedHex)) {
                // حرکت معمولی
                Point startPt  = panel.getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
                Point targetPt = panel.getHexPixelCoords(clickedHex.getQ(), clickedHex.getR());
                panel.startAnimation(selectedUnit, clickedHex,
                        startPt.x, startPt.y, targetPt.x, targetPt.y);
            }
        }
    }
}