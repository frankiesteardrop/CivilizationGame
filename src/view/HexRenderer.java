package view;

import controller.UnitController;
import model.*;

import java.awt.*;

public class HexRenderer {

    public void renderAll(Graphics2D g2d, GamePanel panel, GameMap map, UnitController unitController) {
        Rectangle viewRect = new Rectangle(0, 0, panel.getWidth(), panel.getHeight());
        java.util.List<Hex> visibleHexes = new java.util.ArrayList<>();
        int sz = (int)(GamePanel.HEX_SIZE * panel.getZoomFactor());

        for (Hex hex : map.getHexes()) {
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (pt.x + sz * 2 >= viewRect.x && pt.x - sz * 2 <= viewRect.x + viewRect.width &&
                    pt.y + sz * 2 >= viewRect.y && pt.y - sz * 2 <= viewRect.y + viewRect.height) {
                visibleHexes.add(hex);
            }
        }

        for (Hex hex : visibleHexes) drawHexTerrain(g2d, hex, panel);

        for (Hex hex : visibleHexes)
            if (hex.isInsideBorder() && (hex.isExplored() || hex.isVisible()))
                drawHexBorder(g2d, hex, panel);

        drawMovementHighlights(g2d, panel, map, unitController, visibleHexes);

        for (Hex hex : visibleHexes)
            if (hex.isExplored() || hex.isVisible()) drawHexDetails(g2d, hex, panel);

        for (Hex hex : visibleHexes) {
            if (hex.isExplored() && !hex.isVisible()) {
                Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
                Polygon polygon = createHexPolygon(pt, sz);
                g2d.setColor(new Color(0, 0, 0, 160));
                g2d.fillPolygon(polygon);
            }
        }
    }

    private void drawMovementHighlights(Graphics2D g2d, GamePanel panel, GameMap map, UnitController unitController, java.util.List<Hex> visibleHexes) {
        Unit selectedUnit = panel.getSelectedUnit();
        if (selectedUnit == null || panel.isAnimating()) return;
        if (selectedUnit instanceof Worker && ((Worker) selectedUnit).isStationed()) return;

        for (Hex hex : visibleHexes) {
            int dist = map.getHexDistance(selectedUnit.getQ(), selectedUnit.getR(), hex.getQ(), hex.getR());
            if (dist != 1) continue;

            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            int sz = (int)(GamePanel.HEX_SIZE * panel.getZoomFactor());
            Polygon polygon = createHexPolygon(pt, sz);

            if (unitController.canMove(selectedUnit, hex)) {
                g2d.setColor(UIConfig.MOVE_VALID_FILL);
                g2d.fillPolygon(polygon);
                g2d.setStroke(new BasicStroke((float)(2.5 * panel.getZoomFactor())));
                g2d.setColor(UIConfig.MOVE_VALID_BORDER);
                g2d.drawPolygon(polygon);
                g2d.setStroke(new BasicStroke(1f));
            } else {
                g2d.setColor(UIConfig.MOVE_INVALID_FILL);
                g2d.fillPolygon(polygon);
                g2d.setStroke(new BasicStroke((float)(2.0 * panel.getZoomFactor()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 10.0f, new float[]{6.0f, 6.0f}, 0.0f));
                g2d.setColor(UIConfig.MOVE_INVALID_BORDER);
                g2d.drawPolygon(polygon);
                g2d.setStroke(new BasicStroke(1f));
            }
        }
    }

    private void drawHexTerrain(Graphics2D g2d, Hex hex, GamePanel panel) {
        Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
        int sz = (int)(GamePanel.HEX_SIZE * panel.getZoomFactor());
        Polygon polygon = createHexPolygon(pt, sz);

        if (!hex.isExplored() && !hex.isVisible()) {
            g2d.setColor(UIConfig.UNEXPLORED_FILL);
            g2d.fillPolygon(polygon);
            g2d.setStroke(new BasicStroke(1f));
            g2d.setColor(UIConfig.UNEXPLORED_BORDER);
            g2d.drawPolygon(polygon);
            return;
        }

        boolean hasActiveResource = !hex.getResources().isEmpty() && !hex.isResourceDepleted();
        boolean hasDepletedResource = !hex.getResources().isEmpty() && hex.isResourceDepleted();

        Color topColor, baseColor;

        switch (hex.getTerrainType()) {
            case FOREST:
                if (hasActiveResource) { topColor = UIConfig.FOREST_TOP_ACTIVE; baseColor = UIConfig.FOREST_BASE_ACTIVE; }
                else if (hasDepletedResource) { topColor = UIConfig.FOREST_TOP_DEPLETED; baseColor = UIConfig.FOREST_BASE_DEPLETED; }
                else { topColor = UIConfig.FOREST_TOP_NORMAL; baseColor = UIConfig.FOREST_BASE_NORMAL; }
                break;
            case PLAINS:
                if (hasActiveResource) { topColor = UIConfig.PLAINS_TOP_ACTIVE; baseColor = UIConfig.PLAINS_BASE_ACTIVE; }
                else { topColor = UIConfig.PLAINS_TOP_NORMAL; baseColor = UIConfig.PLAINS_BASE_NORMAL; }
                break;
            case MOUNTAIN:
                if (hasActiveResource) {
                    if (hex.hasResource(ResourceType.IRON)) { topColor = UIConfig.MOUNTAIN_TOP_IRON; baseColor = UIConfig.MOUNTAIN_BASE_IRON; }
                    else { topColor = UIConfig.MOUNTAIN_TOP_STONE; baseColor = UIConfig.MOUNTAIN_BASE_STONE; }
                } else if (hasDepletedResource) { topColor = UIConfig.MOUNTAIN_TOP_DEPLETED; baseColor = UIConfig.MOUNTAIN_BASE_DEPLETED; }
                else { topColor = UIConfig.MOUNTAIN_TOP_NORMAL; baseColor = UIConfig.MOUNTAIN_BASE_NORMAL; }
                break;
            case MEADOW:
                if (hasActiveResource) { topColor = UIConfig.MEADOW_TOP_ACTIVE; baseColor = UIConfig.MEADOW_BASE_ACTIVE; }
                else { topColor = UIConfig.MEADOW_TOP_NORMAL; baseColor = UIConfig.MEADOW_BASE_NORMAL; }
                break;
            default:
                topColor = Color.GRAY; baseColor = Color.DARK_GRAY; break;
        }

        GradientPaint gp = new GradientPaint(pt.x, pt.y - sz, topColor, pt.x, pt.y + sz, baseColor);
        g2d.setPaint(gp);
        g2d.fillPolygon(polygon);

        if (hasDepletedResource) {
            g2d.setColor(new Color(40, 40, 40, 100));
            g2d.fillPolygon(polygon);
        }

        if (hex == panel.getHoveredHex() && panel.getSelectedUnit() == null) {
            g2d.setColor(new Color(255, 255, 255, 50));
            g2d.fillPolygon(polygon);
        }

        g2d.setStroke(new BasicStroke(1.5f));
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.drawPolygon(polygon);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawHexBorder(Graphics2D g2d, Hex hex, GamePanel panel) {
        Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
        int sz = (int)(GamePanel.HEX_SIZE * panel.getZoomFactor());
        Polygon polygon = createHexPolygon(pt, sz);
        g2d.setStroke(new BasicStroke((float)(4.0 * panel.getZoomFactor())));
        g2d.setColor(new Color(255, 215, 0, 180));
        g2d.drawPolygon(polygon);
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawHexDetails(Graphics2D g2d, Hex hex, GamePanel panel) {
        Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
        int offset = 0;
        for (ResourceType rt : hex.getResources().keySet()) {
            if (rt == ResourceType.NONE) continue;
            drawResourceIcon(g2d, hex, new Point(pt.x + offset, pt.y), rt, panel.getZoomFactor());
            offset += (int)(18 * panel.getZoomFactor());
        }

        if (hex.getBuilding() != null) {
            drawBuildingIcon(g2d, hex.getBuilding(), pt, (int)(22 * panel.getZoomFactor()), panel.getZoomFactor());
        }
    }

    private void drawResourceIcon(Graphics2D g2d, Hex hex, Point pt, ResourceType rt, double zoomFactor) {
        if (rt == ResourceType.NONE) return;

        int iconSize = Math.max(8, (int)(20 * zoomFactor));
        int iconX = pt.x - (int)(GamePanel.HEX_SIZE * 0.55 * zoomFactor);
        int iconY = pt.y - (int)(GamePanel.HEX_SIZE * 0.70 * zoomFactor);

        if (hex.getResources().getOrDefault(rt, 0) <= 0) {
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

        Color bgColor, borderColor, textColor;
        String symbol;

        switch (rt) {
            case WOOD: bgColor = UIConfig.RES_WOOD_BG; borderColor = UIConfig.RES_WOOD_BORDER; textColor = UIConfig.RES_WOOD_TEXT; symbol = "W"; break;
            case STONE: bgColor = UIConfig.RES_STONE_BG; borderColor = UIConfig.RES_STONE_BORDER; textColor = UIConfig.RES_STONE_TEXT; symbol = "S"; break;
            case IRON: bgColor = UIConfig.RES_IRON_BG; borderColor = UIConfig.RES_IRON_BORDER; textColor = UIConfig.RES_IRON_TEXT; symbol = "I"; break;
            case FOOD:
                ResourceSubtype st = hex.getResourceSubtype();
                if (st == ResourceSubtype.WHEAT) { bgColor = UIConfig.RES_WHEAT_BG; borderColor = UIConfig.RES_WHEAT_BORDER; textColor = UIConfig.RES_WHEAT_TEXT; symbol = "Wh"; }
                else if (st == ResourceSubtype.RICE) { bgColor = UIConfig.RES_RICE_BG; borderColor = UIConfig.RES_RICE_BORDER; textColor = UIConfig.RES_RICE_TEXT; symbol = "Ri"; }
                else if (st == ResourceSubtype.CATTLE) { bgColor = UIConfig.RES_CATTLE_BG; borderColor = UIConfig.RES_CATTLE_BORDER; textColor = UIConfig.RES_CATTLE_TEXT; symbol = "Ca"; }
                else if (st == ResourceSubtype.SHEEP) { bgColor = UIConfig.RES_SHEEP_BG; borderColor = UIConfig.RES_SHEEP_BORDER; textColor = UIConfig.RES_SHEEP_TEXT; symbol = "Sh"; }
                else { bgColor = UIConfig.RES_FOOD_GENERIC_BG; borderColor = UIConfig.RES_FOOD_GENERIC_BORDER; textColor = UIConfig.RES_FOOD_GENERIC_TEXT; symbol = "F"; }
                break;
            default: return;
        }

        g2d.setColor(bgColor);
        g2d.fillOval(iconX - iconSize/2, iconY - iconSize/2, iconSize, iconSize);
        g2d.setColor(borderColor);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(iconX - iconSize/2, iconY - iconSize/2, iconSize, iconSize);
        g2d.setStroke(new BasicStroke(1f));

        int fontSize = Math.max(6, (int)(10 * zoomFactor));
        if (fontSize > 5) {
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fontSize));
            g2d.setColor(textColor);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(symbol, iconX - fm.stringWidth(symbol) / 2, iconY + fm.getAscent() / 2 - 1);
        }
    }

    private void drawBuildingIcon(Graphics2D g2d, Building b, Point pt, int size, double zoomFactor) {
        if (b.getType() == BuildingType.TOWN_HALL) {
            int w = (int)(40 * zoomFactor);
            int h = (int)(40 * zoomFactor);
            int bx = pt.x - w/2;
            int by = pt.y - h/2;

            g2d.setColor(new Color(0, 0, 0, 120));
            g2d.fillRoundRect(bx + 3, by + 5, w, h, 12, 12);
            GradientPaint bg = new GradientPaint(bx, by, new Color(60, 50, 20), bx, by + h, new Color(30, 25, 10));
            g2d.setPaint(bg);
            g2d.fillRoundRect(bx, by, w, h, 12, 12);
            g2d.setColor(new Color(255, 215, 0));
            g2d.setStroke(new BasicStroke((float)(2.5 * zoomFactor)));
            g2d.drawRoundRect(bx, by, w, h, 12, 12);
            g2d.setStroke(new BasicStroke(1f));

            int fs = (int)(16 * zoomFactor);
            if (fs > 6) {
                g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fs));
                g2d.setColor(new Color(255, 215, 0));
                FontMetrics fm = g2d.getFontMetrics();
                g2d.drawString("TH", pt.x - fm.stringWidth("TH") / 2, pt.y + fm.getAscent() / 2 - 2);
            }
            return;
        }

        int x = pt.x;
        int y = pt.y + 4;
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
            case SETTLEMENT:
                g2d.setColor(new Color(120, 85, 185));
                Polygon house = new Polygon();
                house.addPoint(x, y - size/2); house.addPoint(x + size/2, y); house.addPoint(x + size/2, y + size/2);
                house.addPoint(x - size/2, y + size/2); house.addPoint(x - size/2, y);
                g2d.fillPolygon(house);
                g2d.setColor(new Color(200, 180, 240));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawPolygon(house);
                g2d.setStroke(new BasicStroke(1f));
                break;
            case FARM:
                g2d.setColor(new Color(210, 175, 40));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(110, 75, 20));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                for (int i = -1; i <= 1; i++) { int lx = x + i * (size / 3); g2d.drawLine(lx, y - size/4, lx, y + size/4); }
                g2d.setStroke(new BasicStroke(1f));
                break;
            case STABLE:
                g2d.setColor(new Color(140, 90, 35));
                g2d.fillRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(190, 150, 80));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawRect(x - size/2, y - size/4, size, size/2);
                g2d.setColor(new Color(80, 50, 20));
                g2d.fillRect(x - size/6, y, size/3, size/4);
                g2d.setStroke(new BasicStroke(1f));
                break;
            case LUMBER_MILL:
                g2d.setColor(new Color(110, 65, 20));
                g2d.fillRect(x - size/2, y, size, size/2);
                g2d.setColor(new Color(190, 185, 175));
                g2d.fillOval(x - size/4, y - size/3, size/2, size/2);
                g2d.setColor(new Color(80, 75, 70));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawOval(x - size/4, y - size/3, size/2, size/2);
                g2d.drawLine(x, y - size/3, x, y + size/6);
                g2d.setStroke(new BasicStroke(1f));
                break;
            case STONE_MINE:
                g2d.setColor(new Color(130, 125, 120));
                Polygon smine = new Polygon();
                smine.addPoint(x, y - size/2); smine.addPoint(x + size/2, y + size/2); smine.addPoint(x - size/2, y + size/2);
                g2d.fillPolygon(smine);
                g2d.setColor(new Color(30, 28, 25));
                g2d.fillArc(x - size/5, y + size/6, size/2 - size/10, size/3, 0, 180);
                break;
            case IRON_MINE:
                g2d.setColor(new Color(120, 90, 60));
                Polygon imine = new Polygon();
                imine.addPoint(x, y - size/2); imine.addPoint(x + size/2, y + size/2); imine.addPoint(x - size/2, y + size/2);
                g2d.fillPolygon(imine);
                g2d.setColor(new Color(200, 130, 50));
                g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
                g2d.drawPolygon(imine);
                g2d.setColor(new Color(30, 28, 25));
                g2d.setStroke(new BasicStroke(1f));
                g2d.fillArc(x - size/5, y + size/6, size/2 - size/10, size/3, 0, 180);
                break;
            default: break;
        }

        int fs = (int)(11 * zoomFactor);
        if (fs > 5) {
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fs));
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

    public static Polygon createHexPolygon(Point pt, int size) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 180.0 * (60 * i - 30);
            polygon.addPoint(pt.x + (int)(size * Math.cos(angle)), pt.y + (int)(size * Math.sin(angle)));
        }
        return polygon;
    }
}