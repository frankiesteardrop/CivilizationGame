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

        // یونیت روی دریا → نمایش قایق (فقط اگر Seafaring unlock شده)
        boolean isFloating = (unitHex != null
                && unitHex.getTerrainType() == TerrainType.SEA
                && map.getTownHall().isSeafaringUnlocked());

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

        // ─── نمایش قایق برای یونیت‌های شناور روی دریا ──────────────────────────
        if (isFloating && !isStationed) {
            drawBoatUnit(g2d, u, panel, px, py, zoomFactor);
            return;
        }

        // ─── نمایش معمولی (خشکی) ────────────────────────────────────────────────
        int baseRadius = Math.max(6, (int)(16 * zoomFactor));
        int radius = (u == panel.getSelectedUnit() && !isStationed)
                ? (int)(baseRadius * panel.getPulseScale()) : baseRadius;

        String typeLetter;
        Color unitColor;
        if      (u instanceof Explorer)       { unitColor = UIConfig.UNIT_EXPLORER;  typeLetter = "E"; }
        else if (u instanceof Builder)        { unitColor = UIConfig.UNIT_BUILDER;   typeLetter = "B"; }
        else if (u instanceof Worker)         { unitColor = UIConfig.UNIT_WORKER;    typeLetter = "W"; }
        else if (u instanceof BorderExpander) { unitColor = UIConfig.UNIT_EXPANDER;  typeLetter = "X"; }
        else if (u instanceof Swordsman)      { unitColor = UIConfig.UNIT_SWORDSMAN; typeLetter = "S"; }
        else if (u instanceof Archer)         { unitColor = UIConfig.UNIT_ARCHER;    typeLetter = "A"; }
        else if (u instanceof Cavalry)        { unitColor = UIConfig.UNIT_CAVALRY;   typeLetter = "C"; }
        else if (u instanceof Bear)           { unitColor = UIConfig.UNIT_BEAR;      typeLetter = "🐻"; }
        else                                  { unitColor = Color.GRAY;              typeLetter = "U"; }

        if (isStationed) {
            radius = Math.max(4, (int)(10 * zoomFactor));
            px += (int)(18 * zoomFactor);
            py -= (int)(8 * zoomFactor);
            g2d.setColor(UIConfig.UNIT_STATIONED_AURA);
            g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
            g2d.drawOval(px - radius - 3, py - radius - 3, (radius + 3) * 2, (radius + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        // سایه
        g2d.setColor(new Color(0, 0, 0, 160));
        g2d.fillOval(px - radius + 3, py - radius + 4, radius * 2, radius * 2);

        // بدنه اصلی با gradient
        GradientPaint gp = new GradientPaint(px, py - radius, unitColor.brighter(), px, py + radius, unitColor.darker());
        g2d.setPaint(gp);
        g2d.fillOval(px - radius, py - radius, radius * 2, radius * 2);

        // حاشیه سفید
        g2d.setColor(Color.WHITE);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawOval(px - radius, py - radius, radius * 2, radius * 2);
        g2d.setStroke(new BasicStroke(1f));

        // متن (حرف نوع + AP)
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

        // هاله انتخاب
        if (u == panel.getSelectedUnit()) {
            g2d.setColor(UIConfig.UNIT_SELECTED_AURA);
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawOval(px - radius - 5, py - radius - 5, radius * 2 + 10, radius * 2 + 10);
            g2d.setStroke(new BasicStroke(1f));
        }
    }

    /**
     * رسم یونیت شناور روی دریا به شکل قایق.
     * طبق spec: "یونیت به شکل یک قایق نمایش داده می‌شود".
     * قایق شامل: بدنه (hull) به شکل نیم‌بیضی، دکل عمودی، بادبان مثلثی.
     * رنگ قایق: آبی فیروزه‌ای برای همه یونیت‌ها (نماد شناور بودن).
     */
    private void drawBoatUnit(Graphics2D g2d, Unit u, GamePanel panel, int px, int py, double zoomFactor) {
        int w = (int)(28 * zoomFactor);  // عرض قایق
        int h = (int)(14 * zoomFactor);  // ارتفاع hull
        int mastH = (int)(18 * zoomFactor); // ارتفاع دکل

        // سایه
        g2d.setColor(new Color(0, 0, 0, 100));
        g2d.fillOval(px - w/2 + 2, py - h/4 + 4, w, h/2);

        // بدنه قایق (hull) — نیم‌بیضی
        GradientPaint hullGp = new GradientPaint(
                px, py - h/2, new Color(30, 120, 180),
                px, py + h/2, new Color(10, 70, 130));
        g2d.setPaint(hullGp);
        g2d.fillArc(px - w/2, py - h/2, w, h, 0, -180);

        // حاشیه hull
        g2d.setColor(new Color(100, 200, 255));
        g2d.setStroke(new BasicStroke((float)(1.5 * zoomFactor)));
        g2d.drawArc(px - w/2, py - h/2, w, h, 0, -180);
        g2d.drawLine(px - w/2, py - h/2, px + w/2, py - h/2); // خط عرشه
        g2d.setStroke(new BasicStroke(1f));

        // دکل عمودی
        g2d.setColor(new Color(180, 140, 80));
        g2d.setStroke(new BasicStroke((float)(2.0 * zoomFactor)));
        g2d.drawLine(px, py - h/2, px, py - h/2 - mastH);
        g2d.setStroke(new BasicStroke(1f));

        // بادبان مثلثی
        int[] sailX = { px, px, px + (int)(w * 0.4) };
        int[] sailY = { py - h/2 - mastH, py - h/2 - (int)(mastH * 0.2), py - h/2 - (int)(mastH * 0.5) };
        g2d.setColor(new Color(240, 240, 220, 200));
        g2d.fillPolygon(sailX, sailY, 3);
        g2d.setColor(new Color(180, 180, 160));
        g2d.setStroke(new BasicStroke((float)(1.0 * zoomFactor)));
        g2d.drawPolygon(sailX, sailY, 3);
        g2d.setStroke(new BasicStroke(1f));

        // حرف نوع یونیت روی hull
        int fs = (int)(11 * zoomFactor);
        if (fs > 5) {
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fs));
            String typeLetter;
            if      (u instanceof Swordsman)      typeLetter = "S";
            else if (u instanceof Archer)         typeLetter = "A";
            else if (u instanceof Cavalry)        typeLetter = "C";
            else if (u instanceof Explorer)       typeLetter = "E";
            else if (u instanceof Builder)        typeLetter = "B";
            else if (u instanceof Worker)         typeLetter = "W";
            else if (u instanceof BorderExpander) typeLetter = "X";
            else                                  typeLetter = "?";

            String text = typeLetter + u.getCurrentAP();
            FontMetrics fm = g2d.getFontMetrics();
            int tx = px - fm.stringWidth(text) / 2;
            int ty = py - h/4 + fm.getAscent() / 2;
            g2d.setColor(Color.BLACK);
            g2d.drawString(text, tx + 1, ty + 1);
            g2d.setColor(Color.WHITE);
            g2d.drawString(text, tx, ty);
        }

        // هاله انتخاب
        if (u == panel.getSelectedUnit()) {
            g2d.setColor(UIConfig.UNIT_SELECTED_AURA);
            g2d.setStroke(new BasicStroke((float)(3.0 * zoomFactor)));
            g2d.drawRoundRect(px - w/2 - 5, py - h/2 - mastH - 5, w + 10, mastH + h/2 + 10, 8, 8);
            g2d.setStroke(new BasicStroke(1f));
        }
    }
}