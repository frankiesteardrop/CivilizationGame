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
        Hex current = panel.getHexAtPixel(e.getPoint(), mainController.getGameMap().getHexes());
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
        else if (e.getWheelRotation() > 0 && zoomIndex > 0)                           zoomIndex--;

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

        // ─── ۱. ساختمان‌های ویژه روی hex کلیک‌شده ────────────────────────────
        if (clickedHex.getBuilding() != null && !clickedHex.getBuilding().isDestroyed()) {
            BuildingType bType = clickedHex.getBuilding().getType();

            if (bType == BuildingType.TOWN_HALL) {
                // کلیک روی TH: اگر یونیت نظامی انتخاب‌شده نیست → باز کردن TH menu
                boolean isMilitarySelected = selectedUnit != null && selectedUnit.getAttackRange() > 0;
                if (!isMilitarySelected) {
                    panel.setSelectedUnit(null);
                    panel.showContextMenu(e.getPoint(), mainController.getTownHallMenuActions());
                    return;
                }
            }

            if (bType == BuildingType.TRIBE_CAMP) {
                TribeCamp camp = (TribeCamp) clickedHex.getBuilding();
                boolean isEnemy = camp.getTribe().getRelationship() <= -50;

                // یونیت نظامی + کمپ دشمن → Attack menu
                if (selectedUnit != null && selectedUnit.getAttackRange() > 0 && isEnemy) {
                    panel.showContextMenu(e.getPoint(),
                            mainController.getUnitMenuActions(selectedUnit, clickedHex));
                    return;
                }

                // غیر دشمن یا بدون یونیت نظامی → Tribe interaction
                panel.setSelectedUnit(null);
                panel.onTribeInteractionTriggered(clickedHex);
                return;
            }
        }

        // ─── ۲. یونیت انتخاب‌شده وجود دارد ───────────────────────────────────
        if (selectedUnit != null) {
            boolean isSameHex = (selectedUnit.getQ() == clickedHex.getQ()
                    && selectedUnit.getR() == clickedHex.getR());

            if (isSameHex) {
                // کلیک روی hex خود یونیت → منوی actions
                panel.showContextMenu(e.getPoint(),
                        mainController.getUnitMenuActions(selectedUnit, clickedHex));

            } else {
                // کلیک روی hex دیگر
                // F-28: استفاده از getType() به جای getSimpleName() — امن و type-safe
                boolean hasEnemyUnit = mainController.getGameMap().getUnits().stream()
                        .anyMatch(u -> u.isAlive()
                                && u.getQ() == clickedHex.getQ()
                                && u.getR() == clickedHex.getR()
                                && u.getType() == UnitType.BEAR);

                // F-28: TribeCamp فقط اگر رابطه دشمنانه باشد enemy حساب می‌شود
                boolean hasEnemyBuilding =
                        (clickedHex.getBuilding() instanceof TribeCamp)
                                && ((TribeCamp) clickedHex.getBuilding())
                                .getTribe().getRelationship() <= -50;

                boolean hasEnemy = hasEnemyUnit || hasEnemyBuilding;

                if (hasEnemy && selectedUnit.getAttackRange() > 0) {
                    // F-09: باز کردن attack menu
                    panel.showContextMenu(e.getPoint(),
                            mainController.getUnitMenuActions(selectedUnit, clickedHex));

                } else if (mainController.canMove(selectedUnit, clickedHex)) {
                    // F-10: canMove اکنون map را پاس می‌دهد
                    Point startPt  = panel.getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
                    Point targetPt = panel.getHexPixelCoords(clickedHex.getQ(), clickedHex.getR());
                    panel.startAnimation(selectedUnit, clickedHex,
                            startPt.x, startPt.y, targetPt.x, targetPt.y);
                }
            }
        }
    }
}