package view;

import controller.MainController;
import controller.MenuAction;
import model.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.List;

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
        Unit selectedUnit = panel.getSelectedUnit();

        if (selectedUnit != null) {
            if (selectedUnit.getQ() == clickedHex.getQ() && selectedUnit.getR() == clickedHex.getR()) {
                showMenu(e, mainController.getUnitMenuActions(selectedUnit, clickedHex));
            } else if (!panel.isAnimating()) {
                // کنترلر مستقیماً می‌گوید که آیا حرکت مجاز است (بدون نیاز به چک کردن station)
                if (mainController.canMove(selectedUnit, clickedHex)) {
                    Point startPt = panel.getHexPixelCoords(selectedUnit.getQ(), selectedUnit.getR());
                    Point targetPt = panel.getHexPixelCoords(clickedHex.getQ(), clickedHex.getR());
                    panel.startAnimation(selectedUnit, clickedHex, startPt.x, startPt.y, targetPt.x, targetPt.y);
                }
            }
            return;
        }

        if (clickedHex.getBuilding() != null && clickedHex.getBuilding().getType() == BuildingType.TOWN_HALL) {
            showMenu(e, mainController.getTownHallMenuActions());
        }
    }

    // این متد جنریک تمام منوها را از روی دیتای کنترلر می‌سازد بدون اینکه قوانین را بداند
    private void showMenu(MouseEvent e, List<MenuAction> actions) {
        if (actions == null || actions.isEmpty()) return;

        JPopupMenu popup = new JPopupMenu();
        popup.setBackground(new Color(30, 33, 40));
        popup.setBorder(BorderFactory.createLineBorder(new Color(70, 130, 180), 1));

        for (MenuAction action : actions) {
            JMenuItem item = new JMenuItem(action.getLabel());
            item.setBackground(new Color(30, 33, 40));
            item.setForeground(Color.WHITE);
            item.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.PLAIN, 13));
            item.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

            if (!action.isEnabled()) {
                item.setEnabled(false);
            } else {
                item.addActionListener(ev -> {
                    action.execute();
                    panel.setSelectedUnit(null); // Deselect after action
                    panel.repaint();
                });
            }
            popup.add(item);
        }

        popup.show(panel, e.getX(), e.getY());
    }
}