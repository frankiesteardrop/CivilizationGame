package view;

import model.*;
import java.awt.*;

public class UnitRenderer {

    public void renderAll(Graphics2D g2d, GamePanel panel, GameMap map) {
        Rectangle viewRect = new Rectangle(0, 0, panel.getWidth(), panel.getHeight());
        java.util.Map<String, java.util.List<Unit>> unitStacks = new java.util.HashMap<>();

        for (Unit u : map.getUnits()) {
            if (u.isAlive()) {
                String key = u.getQ() + "," + u.getR();
                unitStacks.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(u);
            }
        }

        for (java.util.List<Unit> stack : unitStacks.values()) {
            int stackSize = stack.size();
            for (int i = 0; i < stackSize; i++) {
                Unit u = stack.get(i);
                Point pt = panel.getHexPixelCoords(u.getQ(), u.getR());
                int sz = (int)(GamePanel.HEX_SIZE * panel.getZoomFactor() * 2);

                if (pt.x + sz >= viewRect.x && pt.x - sz <= viewRect.x + viewRect.width &&
                        pt.y + sz >= viewRect.y && pt.y - sz <= viewRect.y + viewRect.height) {
                    drawUnit(g2d, u, panel, map, i, stackSize);
                }
            }
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u, GamePanel panel, GameMap map, int stackIndex, int stackSize) {
        boolean isStationed = (u instanceof Worker && ((Worker) u).isStationed());

        Hex unitHex = map.getHexAt(u.getQ(), u.getR());
        if (unitHex != null && !unitHex.isExplored()) return;

        int px, py;
        if (u == panel.getAnimatingUnit()) {
            double easeOut = 1.0 - Math.pow(1.0 - panel.getAnimProgress(), 3);
            px = (int)(panel.getAnimStartX() + (panel.getAnimTargetX() - panel.getAnimStartX()) * easeOut);
            py = (int)(panel.getAnimStartY() + (panel.getAnimTargetY() - panel.getAnimStartY()) * easeOut);
        } else {
            Point pt = panel.getHexPixelCoords(u.getQ(), u.getR());
            px = pt.x;
            py = pt.y;

            if (!isStationed && stackSize > 1) {
                int offsetStep = (int)(6 * panel.getZoomFactor());
                int totalOffset = (stackSize - 1) * offsetStep;
                px += (stackIndex * offsetStep) - (totalOffset / 2);
                py -= (stackIndex * offsetStep) - (totalOffset / 2);
            }
        }

        double zoomFactor = panel.getZoomFactor();
        int baseRadius = Math.max(6, (int)(16 * zoomFactor));
        int radius = (u == panel.getSelectedUnit() && !isStationed)
                ? (int)(baseRadius * panel.getPulseScale()) : baseRadius;

        String typeLetter;
        Color unitColor;
        if      (u instanceof Explorer)       { unitColor = UIConfig.UNIT_EXPLORER; typeLetter = "E"; }
        else if (u instanceof Builder)        { unitColor = UIConfig.UNIT_BUILDER; typeLetter = "B"; }
        else if (u instanceof Worker)         { unitColor = UIConfig.UNIT_WORKER; typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = UIConfig.UNIT_EXPANDER; typeLetter = "X"; }
        else                                  { unitColor = Color.GRAY; typeLetter = "U"; }

        if (isStationed) {
            radius = Math.max(4, (int)(10 * zoomFactor));
            px += (int)(18 * zoomFactor);
            py -= (int)(8 * zoomFactor);
            g2d.setColor(UIConfig.UNIT_STATIONED_AURA);
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawOval(px - radius - 3, py - radius - 3, (radius + 3) * 2, (radius + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillOval(px - radius + 3, py - radius + 4, radius * 2, radius * 2);

        GradientPaint gp = new GradientPaint(px, py - radius, unitColor.brighter(), px, py + radius, unitColor.darker());
        g2d.setPaint(gp);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setStroke(new BasicStroke(1f));

        int fs = (int)(13 * zoomFactor);
        if (fs > 6 && !isStationed) {
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fs));
            String text = typeLetter + u.getCurrentAP();
            FontMetrics fm = g2d.getFontMetrics();
            int tx = px - fm.stringWidth(text) / 2;
            int ty = py + fm.getAscent() / 2 - 2;
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, tx + 1, ty + 1);
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }

        if (u == panel.getSelectedUnit()) {
            g2d.setColor(UIConfig.UNIT_SELECTED_AURA);
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawOval(px - radius - 5, py - radius - 5, radius * 2 + 10, radius * 2 + 10);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}