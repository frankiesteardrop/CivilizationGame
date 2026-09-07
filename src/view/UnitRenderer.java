package view;

import controller.UnitController;
import model.*;

import java.awt.*;
import java.awt.geom.*;

public class UnitRenderer {

    public void renderAll(Graphics2D g2d, GamePanel panel, GameMap map, String myPlayerId) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

        java.util.Map<String, java.util.List<Unit>> stacks = new java.util.LinkedHashMap<>();
        for (Unit u : map.getUnits()) {
            if (!u.isAlive()) continue;
            String key = u.getQ() + "," + u.getR();
            stacks.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(u);
        }

        for (java.util.List<Unit> stack : stacks.values()) {
            if (stack.isEmpty()) continue;

            Hex hex = map.getHexAt(stack.get(0).getQ(), stack.get(0).getR());
            if (hex == null || (!hex.isVisible(myPlayerId) && !hex.isExplored(myPlayerId))) continue;
            if (!hex.isVisible(myPlayerId)) continue;

            int stackSize = stack.size();
            for (int i = 0; i < stackSize; i++) {
                drawUnit(g2d, stack.get(i), panel, map, i, stackSize, myPlayerId);
            }

            if (stackSize > 1) {
                Point pt = panel.getHexPixelCoords(stack.get(0).getQ(), stack.get(0).getR());
                drawStackBadge(g2d, stackSize, pt.x, pt.y, panel.getZoomFactor());
            }
        }
    }

    private void drawUnit(Graphics2D g2d, Unit u, GamePanel panel, GameMap map,
                          int stackIndex, int stackSize, String myPlayerId) {
        boolean isStationed = (u instanceof Worker && ((Worker) u).isStationed());

        Hex unitHex = map.getHexAt(u.getQ(), u.getR());
        if (unitHex == null || !unitHex.isVisible(myPlayerId)) return;

        boolean isFloating = (unitHex.getTerrainType() == TerrainType.SEA
                && map.getPlayerTownHall(u.getOwnerId()).isSeafaringUnlocked());

        double zoom = panel.getZoomFactor();
        int    baseR = (int)(14 * zoom);

        int px, py;
        int shadowOffset = 0;
        if (u == panel.getAnimatingUnit()) {
            double progress = panel.getAnimProgress();
            double ease = 1.0 - Math.pow(1.0 - progress, 3);

            px = (int)(panel.getAnimStartX() + (panel.getAnimTargetX() - panel.getAnimStartX()) * ease);
            py = (int)(panel.getAnimStartY() + (panel.getAnimTargetY() - panel.getAnimStartY()) * ease);

            if (!isFloating) {
                int bounceHeight = (int)(8 * zoom);
                shadowOffset = (int)(Math.abs(Math.sin(progress * Math.PI * 4)) * bounceHeight);
                py -= shadowOffset;
            }
        } else {
            Point pt = panel.getHexPixelCoords(u.getQ(), u.getR());
            px = pt.x; py = pt.y;

            if (!isStationed && stackSize > 1) {
                int step = (int)(6 * zoom);
                int total = (stackSize - 1) * step;
                px += stackIndex * step - total / 2;
                py -= stackIndex * step;
            }

            if (isStationed) {
                baseR = (int)(9 * zoom);
                px += (int)(20 * zoom);
                py -= (int)(12 * zoom);
            }
        }

        if (u == panel.getSelectedUnit() && !isStationed) {
            double pulse = panel.getPulseScale();
            int pulseR = (int)(baseR * pulse);

            g2d.setColor(new Color(255, 218, 40, 40));
            g2d.setStroke(new BasicStroke((float)(5 * zoom * pulse)));
            g2d.drawOval(px - pulseR - 4, py - pulseR - 4, (pulseR + 4) * 2, (pulseR + 4) * 2);
            g2d.setColor(UIConfig.UNIT_SELECTED_AURA);
            g2d.setStroke(new BasicStroke((float)(2.5 * zoom)));
            g2d.drawOval(px - pulseR - 1, py - pulseR - 1, (pulseR + 1) * 2, (pulseR + 1) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        if (isStationed) {
            g2d.setColor(UIConfig.UNIT_STATIONED_AURA);
            g2d.setStroke(new BasicStroke((float)(1.5 * zoom)));
            g2d.drawOval(px - baseR - 3, py - baseR - 3, (baseR + 3) * 2, (baseR + 3) * 2);
            g2d.setStroke(new BasicStroke(1f));
        }

        if (!isFloating) {
            g2d.setColor(new Color(0, 0, 0, 100));
            int sr = baseR - (shadowOffset / 2);
            g2d.fillOval(px - sr + 2, py + shadowOffset - sr + 2 + (int)(rToShadowOffset(baseR)), sr*2, sr);
        }

        if (isFloating && !isStationed) {
            drawBoatUnit(g2d, u, panel, px, py, zoom);
        } else if (isMilitary(u.getType())) {
            drawMilitaryUnit(g2d, u, px, py, baseR, zoom, isStationed);
        } else if (u.getType() == UnitType.BEAR) {
            drawBearUnit(g2d, u, px, py, baseR, zoom);
        } else {
            drawCivilianUnit(g2d, u, px, py, baseR, zoom, isStationed);
        }

        if (!isStationed && zoom >= 0.75) {
            drawAPIndicator(g2d, u, px, py + shadowOffset, baseR, zoom);
        }

        if (isMilitary(u.getType()) && u.getHp() < u.getMaxHp() && zoom >= 0.75) {
            drawUnitHPBar(g2d, u, px, py - shadowOffset, baseR, zoom);
        }
    }

    private int rToShadowOffset(int r) {
        return (int)(r * 0.6);
    }

    private void drawMilitaryUnit(Graphics2D g2d, Unit u, int px, int py,
                                  int r, double zoom, boolean stationed) {
        UnitType type   = u.getType();
        Color mainColor = getMilitaryColor(type);
        Color border    = getMilitaryBorder(type);

        g2d.setColor(mainColor.darker());
        drawShieldShape(g2d, px, py, r);
        g2d.setColor(mainColor);
        drawShieldShape(g2d, px - 1, py - 1, (int)(r * 0.88));

        g2d.setColor(new Color(255, 255, 255, 80));
        drawShieldShape(g2d, px - 2, py - 2, (int)(r * 0.6));

        g2d.setColor(border);
        g2d.setStroke(new BasicStroke((float)(1.8 * zoom)));
        drawShieldOutline(g2d, px, py, r);
        g2d.setStroke(new BasicStroke(1f));

        if (zoom >= 0.5) {
            String sym = getMilitarySymbol(type);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, (int)(r * 1.1)));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(sym, px - fm.stringWidth(sym)/2, py + fm.getAscent()/2 - 2);
        }

        if (type == UnitType.CAVALRY && zoom >= 0.75) {
            for (int i = 0; i < u.getMaxHp(); i++) {
                int dotX = px + r - 4 + i * (int)(5 * zoom);
                int dotY = py + r - 3;
                int ds = (int)(3 * zoom);
                g2d.setColor(i < u.getHp() ? new Color(100, 240, 100) : new Color(200, 50, 50));
                g2d.fillOval(dotX, dotY, ds, ds);
            }
        }
    }

    private void drawShieldShape(Graphics2D g2d, int cx, int cy, int r) {
        int[] sx = {cx-r, cx-r, cx, cx+r, cx+r};
        int[] sy = {cy-(int)(r*0.7), cy+(int)(r*0.3), cy+r, cy+(int)(r*0.3), cy-(int)(r*0.7)};
        g2d.fillPolygon(sx, sy, 5);
    }

    private void drawShieldOutline(Graphics2D g2d, int cx, int cy, int r) {
        int[] sx = {cx-r, cx-r, cx, cx+r, cx+r};
        int[] sy = {cy-(int)(r*0.7), cy+(int)(r*0.3), cy+r, cy+(int)(r*0.3), cy-(int)(r*0.7)};
        g2d.drawPolygon(sx, sy, 5);
    }

    private Color getMilitaryColor(UnitType type) {
        return switch (type) {
            case SWORDSMAN -> UIConfig.UNIT_SWORDSMAN;
            case ARCHER    -> UIConfig.UNIT_ARCHER;
            case CAVALRY   -> UIConfig.UNIT_CAVALRY;
            case CATAPULT  -> UIConfig.UNIT_CATAPULT;
            default        -> Color.GRAY;
        };
    }

    private Color getMilitaryBorder(UnitType type) {
        return switch (type) {
            case SWORDSMAN -> UIConfig.UNIT_SWORDSMAN_BORDER;
            case ARCHER    -> UIConfig.UNIT_ARCHER_BORDER;
            case CAVALRY   -> UIConfig.UNIT_CAVALRY_BORDER;
            case CATAPULT  -> UIConfig.UNIT_CATAPULT_BORDER;
            default        -> Color.DARK_GRAY;
        };
    }

    private String getMilitarySymbol(UnitType type) {
        return switch (type) {
            case SWORDSMAN -> "⚔";
            case ARCHER    -> "🏹";
            case CAVALRY   -> "🐎";
            case CATAPULT  -> "💣";
            default        -> "?";
        };
    }

    private void drawCivilianUnit(Graphics2D g2d, Unit u, int px, int py,
                                  int r, double zoom, boolean stationed) {
        Color mainColor = getCivilianColor(u.getType());
        Color border    = getCivilianBorder(u.getType());

        g2d.setColor(mainColor.darker());
        g2d.fillOval(px - r, py - r, r*2, r*2);

        g2d.setColor(mainColor);
        g2d.fillOval(px - r + 1, py - r + 1, r*2 - 2, r*2 - 2);

        g2d.setColor(new Color(255, 255, 255, 70));
        g2d.fillOval(px - r + 2, py - r + 2, (int)(r * 1.0), (int)(r * 0.9));

        g2d.setColor(border);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoom)));
        g2d.drawOval(px - r, py - r, r*2, r*2);
        g2d.setStroke(new BasicStroke(1f));

        if (zoom >= 0.5) {
            String sym = getCivilianSymbol(u.getType());
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, Math.max(6, (int)(r))));
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(sym);
            g2d.drawString(sym, px - tw/2, py + fm.getAscent()/2 - 2);
        }
    }

    private Color getCivilianColor(UnitType type) {
        return switch (type) {
            case WORKER         -> UIConfig.UNIT_WORKER;
            case BUILDER        -> UIConfig.UNIT_BUILDER;
            case EXPLORER       -> UIConfig.UNIT_EXPLORER;
            case BORDER_EXPANDER -> UIConfig.UNIT_EXPANDER;
            default             -> Color.GRAY;
        };
    }

    private Color getCivilianBorder(UnitType type) {
        return switch (type) {
            case WORKER         -> UIConfig.UNIT_WORKER_BORDER;
            case BUILDER        -> UIConfig.UNIT_BUILDER_BORDER;
            case EXPLORER       -> UIConfig.UNIT_EXPLORER_BORDER;
            case BORDER_EXPANDER -> UIConfig.UNIT_EXPANDER_BORDER;
            default             -> Color.DARK_GRAY;
        };
    }

    private String getCivilianSymbol(UnitType type) {
        return switch (type) {
            case WORKER         -> "W";
            case BUILDER        -> "B";
            case EXPLORER       -> "E";
            case BORDER_EXPANDER -> "X";
            default             -> "?";
        };
    }
    private void drawBearUnit(Graphics2D g2d, Unit u, int px, int py,
                              int r, double zoom) {
        Color body   = UIConfig.UNIT_BEAR;
        Color border = UIConfig.UNIT_BEAR_BORDER;

        g2d.setColor(body);
        g2d.fillOval(px - (int)(r*1.1), py - r, (int)(r*2.2), r*2);

        g2d.setColor(body.darker());
        g2d.fillOval(px - (int)(r*0.7), py - (int)(r*0.2), (int)(r*1.4), (int)(r*1.2));

        g2d.setColor(body);
        g2d.fillOval(px - r, py - r - (int)(r*0.6), (int)(r*0.7), (int)(r*0.6));
        g2d.fillOval(px + (int)(r*0.3), py - r - (int)(r*0.6), (int)(r*0.7), (int)(r*0.6));

        g2d.setColor(new Color(30, 20, 15));
        int es = Math.max(2, (int)(2.5 * zoom));
        g2d.fillOval(px - (int)(r*0.4) - es/2, py - (int)(r*0.2) - es/2, es, es);
        g2d.fillOval(px + (int)(r*0.4) - es/2, py - (int)(r*0.2) - es/2, es, es);

        g2d.setColor(border);
        g2d.setStroke(new BasicStroke((float)(1.5 * zoom)));
        g2d.drawOval(px - (int)(r*1.1), py - r, (int)(r*2.2), r*2);
        g2d.setStroke(new BasicStroke(1f));

        drawUnitHPBar(g2d, u, px, py, r, zoom);
    }


    private void drawBoatUnit(Graphics2D g2d, Unit u, GamePanel panel,
                              int px, int py, double zoom) {
        int w  = (int)(26 * zoom);
        int h  = (int)(13 * zoom);
        int mH = (int)(17 * zoom);

        long time = System.currentTimeMillis() + u.hashCode();
        int waterBob = (int)(Math.sin(time / 400.0) * (3 * zoom));
        py += waterBob;
        g2d.setColor(new Color(180, 220, 255, 80));
        g2d.fillOval(px - w/2 - 3, py - (int)(h*0.2) + h/2, w + 6, (int)(h*0.5));

        GradientPaint hullGp = new GradientPaint(
                px, py - h/2, new Color(130, 80, 45),
                px, py + h/2, new Color(75, 45, 20));
        g2d.setPaint(hullGp);
        g2d.fillArc(px - w/2, py - h/2, w, h, 0, -180);
        g2d.setPaint(null);

        g2d.setColor(new Color(40, 20, 10));
        g2d.setStroke(new BasicStroke((float)(1.2 * zoom)));
        g2d.drawArc(px - w/2, py - h/2, w, h, 0, -180);
        g2d.drawLine(px - w/2, py - h/2, px + w/2, py - h/2);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(new Color(60, 30, 15));
        g2d.setStroke(new BasicStroke((float)(2.0 * zoom)));
        g2d.drawLine(px, py - h/2, px, py - h/2 - mH);
        g2d.setStroke(new BasicStroke(1f));

        int[] sailX = {px, px, px + (int)(w * 0.5)};
        int[] sailY = {py - h/2 - mH, py - h/2 - (int)(mH * 0.18), py - h/2 - (int)(mH * 0.52)};

        Color unitColor = isMilitary(u.getType()) ? getMilitaryColor(u.getType()) : getCivilianColor(u.getType());

        GradientPaint sailGp = new GradientPaint(
                px, py - h/2 - mH, unitColor.brighter(),
                px + (int)(w*0.5), py - h/2 - (int)(mH*0.52), unitColor.darker());
        g2d.setPaint(sailGp);
        g2d.fillPolygon(sailX, sailY, 3);
        g2d.setPaint(null);
        g2d.setColor(new Color(255, 255, 255, 120));
        g2d.setStroke(new BasicStroke((float)(0.8 * zoom)));
        g2d.drawPolygon(sailX, sailY, 3);

        if (zoom >= 0.75) {
            String sym = getCivilianSymbol(u.getType());
            if (isMilitary(u.getType())) sym = getMilitarySymbolShort(u.getType());
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, (int)(9 * zoom)));
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(sym, px - fm.stringWidth(sym)/2, py - h/4 + fm.getAscent()/2);
        }

        if (u == panel.getSelectedUnit()) {
            g2d.setColor(UIConfig.UNIT_SELECTED_AURA);
            g2d.setStroke(new BasicStroke((float)(2.5 * zoom)));
            g2d.drawRoundRect(px - w/2 - 5, py - h/2 - mH - 5, w + 10, mH + h/2 + 10, 8, 8);
            g2d.setStroke(new BasicStroke(1f));
        }

        drawAPIndicator(g2d, u, px, py + h/2 + (int)(4*zoom), (int)(8 * zoom), zoom);
    }

    private void drawAPIndicator(Graphics2D g2d, Unit u, int px, int py,
                                 int r, double zoom) {
        int maxAP = u.getMaxAP();
        if (maxAP <= 0) return;

        int pipSize = (int)(3.5 * zoom);
        int gap     = (int)(1.5 * zoom);
        int totalW  = maxAP * pipSize + (maxAP - 1) * gap;
        int startX  = px - totalW / 2;
        int dotY    = py + r + (int)(3 * zoom);

        for (int i = 0; i < maxAP; i++) {
            int dx = startX + i * (pipSize + gap);
            boolean active = i < u.getCurrentAP();

            g2d.setColor(new Color(0, 0, 0, 150));
            g2d.fillOval(dx + 1, dotY + 1, pipSize, pipSize);

            g2d.setColor(active ? new Color(80, 230, 120) : new Color(200, 60, 60, 180));
            g2d.fillOval(dx, dotY, pipSize, pipSize);

            if (active) {
                g2d.setColor(new Color(180, 255, 200, 160));
                g2d.fillOval(dx, dotY, (int)(pipSize * 0.55), (int)(pipSize * 0.55));
            }
        }
    }

    private void drawUnitHPBar(Graphics2D g2d, Unit u, int px, int py,
                               int r, double zoom) {
        int bw = (int)(r * 2.0);
        int bh = (int)(3 * zoom);
        int bx = px - bw / 2;
        int by = py - r - (int)(5 * zoom);

        float pct = (float) u.getHp() / Math.max(1, u.getMaxHp());

        g2d.setColor(new Color(20, 20, 20, 200));
        g2d.fillRoundRect(bx - 1, by - 1, bw + 2, bh + 2, 3, 3);

        Color hpC = pct > 0.60f ? new Color(55, 210, 80)
                : pct > 0.30f ? new Color(230, 185, 30)
                : new Color(215, 52, 52);
        g2d.setColor(hpC);
        g2d.fillRoundRect(bx, by, (int)(bw * pct), bh, 2, 2);

        g2d.setColor(new Color(255, 255, 255, 60));
        g2d.fillRoundRect(bx, by, (int)(bw * pct), bh / 2, 2, 2);
    }

    private void drawStackBadge(Graphics2D g2d, int count, int cx, int cy, double zoom) {
        int bx = cx + (int)(12 * zoom);
        int by = cy - (int)(12 * zoom);
        int bs = (int)(9 * zoom);

        g2d.setColor(new Color(30, 30, 30, 210));
        g2d.fillOval(bx - bs/2, by - bs/2, bs, bs);

        g2d.setColor(new Color(255, 200, 40));
        g2d.setStroke(new BasicStroke((float)(1.2 * zoom)));
        g2d.drawOval(bx - bs/2, by - bs/2, bs, bs);
        g2d.setStroke(new BasicStroke(1f));

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, (int)(bs * 0.75)));
        String txt = String.valueOf(count);
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(txt, bx - fm.stringWidth(txt)/2, by + fm.getAscent()/2 - 1);
    }

    private boolean isMilitary(UnitType type) {
        return type == UnitType.SWORDSMAN || type == UnitType.ARCHER
                || type == UnitType.CAVALRY || type == UnitType.CATAPULT;
    }

    private String getMilitarySymbolShort(UnitType type) {
        return switch (type) {
            case SWORDSMAN -> "S";
            case ARCHER    -> "A";
            case CAVALRY   -> "C";
            case CATAPULT  -> "C";
            default        -> "?";
        };
    }
}