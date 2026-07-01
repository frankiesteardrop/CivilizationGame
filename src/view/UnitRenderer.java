package view;

import model.*;
import java.awt.*;

public class UnitRenderer {

    public void renderAll(Graphics2D g2d, GamePanel panel, GameMap map) {
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) drawUnit(g2d, u, panel, map);
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u, GamePanel panel, GameMap map) {
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
            px = pt.x; py = pt.y;
        }

        double zoomFactor = panel.getZoomFactor();
        int baseRadius = Math.max(6, (int)(16 * zoomFactor));
        int radius = (u == panel.getSelectedUnit() && !isStationed)
                ? (int)(baseRadius * panel.getPulseScale()) : baseRadius;

        String typeLetter;
        Color unitColor;
        if      (u instanceof Explorer)       { unitColor = new Color(65, 105, 225); typeLetter = "E"; }
        else if (u instanceof Builder)        { unitColor = new Color(255, 215, 0); typeLetter = "B"; }
        else if (u instanceof Worker)         { unitColor = new Color(255, 140, 0); typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = new Color(218, 112, 214); typeLetter = "X"; }
        else                                  { unitColor = Color.GRAY; typeLetter = "U"; }

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

        GradientPaint gp = new GradientPaint(px, py - radius, unitColor.brighter(), px, py + radius, unitColor.darker());
        g2d.setPaint(gp);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setStroke(new BasicStroke(1f));

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

        if (u == panel.getSelectedUnit()) {
            g2d.setColor(new Color(0, 255, 255, 200));
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawOval(px - radius - 5, py - radius - 5, radius * 2 + 10, radius * 2 + 10);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}