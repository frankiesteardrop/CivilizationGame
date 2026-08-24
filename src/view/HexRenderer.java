package view;

import controller.UnitController;
import model.*;

import java.awt.*;
import java.awt.geom.*;
import java.util.*;
import java.util.List;

public class HexRenderer {

    private final int[] hxBase = new int[6];
    private final int[] hyBase = new int[6];
    private double cachedHexSize = -1;

    private final int[] hxInner = new int[6];
    private final int[] hyInner = new int[6];

    private void rebuildHexPolygon(double size) {
        if (size == cachedHexSize) return;
        cachedHexSize = size;
        double inner = size * 0.82;
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6.0 + Math.PI / 3.0 * i; // pointy-top
            hxBase[i] = (int) Math.round(Math.cos(angle) * size);
            hyBase[i] = (int) Math.round(Math.sin(angle) * size);
            hxInner[i] = (int) Math.round(Math.cos(angle) * inner);
            hyInner[i] = (int) Math.round(Math.sin(angle) * inner);
        }
    }

    private void setupQuality(Graphics2D g2d) {
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
    }

    public void renderAll(Graphics2D g2d, GamePanel panel, GameMap map, UnitController unitController) {
        setupQuality(g2d);

        double zoom  = panel.getZoomFactor();
        int    size  = (int)(GamePanel.HEX_SIZE * zoom);
        Season season = map.getCurrentSeason();

        rebuildHexPolygon(size);

        int margin = size * 3;
        Rectangle clip = new Rectangle(-margin, -margin,
                panel.getWidth()  + margin * 2,
                panel.getHeight() + margin * 2);

        List<Hex> hexes = map.getHexes();

        // ── Pass 1: Base Terrain ──────────────────────────────────────────────
        for (Hex hex : hexes) {
            if (!hex.isExplored() && !hex.isVisible()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;
            drawTerrainBase(g2d, hex, pt.x, pt.y, size, season, zoom);
        }

        // ── Pass 2: Territory fill ────────────────────────────────────────────
        for (Hex hex : hexes) {
            if (!hex.isVisible() || !hex.isInsideBorder()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;
            drawTerritoryFill(g2d, hex, pt.x, pt.y, size);
        }

        // ── Pass 3-6: Rivers, Roads, Walls, Borders ───────────────────────────
        for (Hex hex : hexes) {
            if (!hex.isVisible() && !hex.isExplored()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;

            drawRivers(g2d, hex, pt.x, pt.y, size, zoom, map, panel);
            if (hex.isVisible() && hex.hasRoad()) drawRoad(g2d, hex, pt.x, pt.y, size, zoom, map, panel);
            drawWalls(g2d, hex, pt.x, pt.y, size, zoom, map, panel);
            if (hex.isInsideBorder()) drawTerritoryBorder(g2d, hex, pt.x, pt.y, size, zoom, map, panel);
        }

        // ── Pass 7: Buildings ─────────────────────────────────────────────────
        for (Hex hex : hexes) {
            if (!hex.isVisible()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed()) {
                drawBuilding(g2d, hex, b, pt.x, pt.y, size, zoom);
            }
        }

        // ── Pass 8: Resource Icons ────────────────────────────────────────────
        for (Hex hex : hexes) {
            if (!hex.isVisible()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;
            if (zoom >= 0.75) drawResourceIcons(g2d, hex, pt.x, pt.y, size, zoom);
        }

        // ── Pass 9 & 10: Highlights & Disasters ───────────────────────────────
        drawHighlights(g2d, panel, map, unitController, clip, size, zoom);
        drawDisasterOverlays(g2d, panel, clip, size);

        // ── Pass 11 & 12: Advanced Fog of War ─────────────────────────────────
        for (Hex hex : hexes) {
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;
            if (hex.isExplored() && !hex.isVisible()) {
                drawAdvancedFog(g2d, pt.x, pt.y, UIConfig.FOG_EXPLORED_DARK, size);
            } else if (!hex.isExplored()) {
                drawAdvancedFog(g2d, pt.x, pt.y, UIConfig.FOG_UNEXPLORED, size);
            }
        }

        // ── Pass 13 & 14: Hover & UI Overlays ─────────────────────────────────
        Hex hovered = panel.getHoveredHex();
        if (hovered != null && hovered.isVisible()) {
            Point pt = panel.getHexPixelCoords(hovered.getQ(), hovered.getR());
            g2d.setColor(UIConfig.HEX_HOVER);
            drawHexAt(g2d, pt.x, pt.y, true);
            drawHexInfoOverlay(g2d, panel, hovered);
        }
    }

    private void drawAdvancedFog(Graphics2D g2d, int cx, int cy, Color color, int size) {
        g2d.translate(cx, cy);
        g2d.setColor(color);
        g2d.fillPolygon(hxBase, hyBase, 6);

        if (color == UIConfig.FOG_UNEXPLORED) {
            g2d.setColor(UIConfig.FOG_PATTERN);
            g2d.setStroke(new BasicStroke(1.5f));
            for (int i = -size; i < size; i += 8) {
                g2d.drawLine(i, -size, i + size, size);
            }
            g2d.setStroke(new BasicStroke(1f));
        }
        g2d.translate(-cx, -cy);
    }

    // ─── Terrain Base ─────────────────────────────────────────────────────────

    private void drawTerrainBase(Graphics2D g2d, Hex hex, int cx, int cy,
                                 int size, Season season, double zoom) {
        TerrainType terrain = hex.getTerrainType();

        g2d.translate(cx, cy);

        switch (terrain) {
            case PLAINS        -> drawPlains(g2d, hex, size, season, zoom);
            case FOREST        -> drawForest(g2d, hex, size, season, zoom);
            case MOUNTAIN      -> drawMountain(g2d, hex, size, season, zoom);
            case MEADOW        -> drawMeadow(g2d, hex, size, season, zoom);
            case SEA           -> drawSea(g2d, size, season, zoom);
            case MOUNTAIN_RANGE -> drawMountainRange(g2d, size, season, zoom);
        }

        g2d.setColor(new Color(0, 0, 0, 55));
        g2d.setStroke(new BasicStroke((float)(0.8 * zoom)));
        g2d.drawPolygon(hxBase, hyBase, 6);
        g2d.setStroke(new BasicStroke(1f));

        g2d.translate(-cx, -cy);
    }

    private void drawPlains(Graphics2D g2d, Hex hex, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_PLAINS;
        Color light = UIConfig.TERRAIN_PLAINS_LIGHT;
        Color dark  = UIConfig.TERRAIN_PLAINS_DARK;

        if (season == Season.WINTER) {
            base  = blendColor(base, new Color(200, 205, 215), 0.35f);
            light = blendColor(light, new Color(215, 218, 228), 0.35f);
        }

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 1.0) {
            Random rng = new Random(hex.getQ() * 31L + hex.getR() * 17L);

            g2d.setColor(new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), 55));
            g2d.setStroke(new BasicStroke((float)(0.6 * zoom)));
            for (int i = 0; i < 12; i++) {
                int gx = (int)((rng.nextDouble() - 0.5) * size * 1.4);
                int gy = (int)((rng.nextDouble() - 0.5) * size * 1.2);
                g2d.drawLine(gx, gy, gx, gy - (int)(zoom * 3));
            }
            g2d.setStroke(new BasicStroke(1f));

            if (hex.hasResource(ResourceType.FOOD)) {
                boolean isSheep = (hex.getResourceSubtype() == ResourceSubtype.SHEEP);
                Color animalColor = isSheep ? UIConfig.VISUAL_ANIMAL_SHEEP : UIConfig.VISUAL_ANIMAL_CATTLE;

                g2d.setColor(animalColor);
                for (int i = 0; i < 5; i++) {
                    int ax = (int)((rng.nextDouble() - 0.5) * size * 0.8);
                    int ay = (int)((rng.nextDouble() - 0.5) * size * 0.8);
                    int aw = (int)(6 * zoom);
                    int ah = (int)(4 * zoom);

                    g2d.setColor(new Color(0, 0, 0, 80));
                    g2d.fillOval(ax, ay + ah/2, aw, ah/2);

                    g2d.setColor(animalColor);
                    if (isSheep) {
                        g2d.fillOval(ax, ay, aw, ah);
                    } else {
                        g2d.fillRect(ax, ay, aw, ah);
                        g2d.setColor(Color.WHITE);
                        g2d.fillRect(ax + aw/2, ay, aw/3, ah);
                    }
                }
            }
        }
    }

    private void drawForest(Graphics2D g2d, Hex hex, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_FOREST;
        Color light = UIConfig.TERRAIN_FOREST_LIGHT;
        Color dark  = UIConfig.TERRAIN_FOREST_DARK;

        if (season == Season.AUTUMN) {
            base  = blendColor(base, new Color(130, 80, 20), 0.40f);
            light = blendColor(light, new Color(170, 105, 25), 0.40f);
        } else if (season == Season.WINTER) {
            base  = blendColor(base, new Color(55, 75, 65), 0.50f);
            light = blendColor(light, new Color(70, 95, 82), 0.50f);
        }

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 0.75) {
            int treeCount = hex.hasResource(ResourceType.WOOD) ? 14 : 5;
            drawProceduralTrees(g2d, hex, size, zoom, light, season, treeCount);
        }
    }

    private void drawProceduralTrees(Graphics2D g2d, Hex hex, int size, double zoom,
                                     Color treeColor, Season season, int count) {
        Random rng = new Random(hex.getQ() * 13L + hex.getR() * 7L);
        Color crown = (season == Season.AUTUMN)
                ? new Color(160, 95, 25)
                : new Color(treeColor.getRed(), treeColor.getGreen(), treeColor.getBlue());
        Color trunk = new Color(90, 60, 30);

        int th = (int)(size * 0.32 * zoom / Math.max(zoom, 0.75));
        int tw = (int)(size * 0.22 * zoom / Math.max(zoom, 0.75));

        List<Point> positions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int tx = (int)((rng.nextDouble() - 0.5) * size * 1.2);
            int ty = (int)((rng.nextDouble() - 0.5) * size * 1.1);
            positions.add(new Point(tx, ty));
        }
        positions.sort(Comparator.comparingInt(p -> p.y));

        for (Point pos : positions) {
            int tx = pos.x, ty = pos.y;

            g2d.setColor(trunk);
            g2d.fillRect(tx - (int)(tw*0.12), ty + (int)(th*0.55), (int)(tw*0.24), (int)(th*0.35));

            int[] xs1 = {tx, tx - tw, tx + tw};
            int[] ys1 = {ty - th + (int)(th*0.3), ty + (int)(th*0.5), ty + (int)(th*0.5)};
            g2d.setColor(crown.darker());
            g2d.fillPolygon(xs1, ys1, 3);

            int[] xs2 = {tx, tx - (int)(tw*0.8), tx + (int)(tw*0.8)};
            int[] ys2 = {ty - th, ty + (int)(th*0.15), ty + (int)(th*0.15)};
            g2d.setColor(crown);
            g2d.fillPolygon(xs2, ys2, 3);
        }
    }

    private void drawMountain(Graphics2D g2d, Hex hex, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_MOUNTAIN;
        Color light = UIConfig.TERRAIN_MOUNTAIN_LIGHT;
        Color dark  = UIConfig.TERRAIN_MOUNTAIN_DARK;

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 0.75) {
            int peakH = (int)(size * 0.55);
            int baseW = (int)(size * 0.65);

            int[] shadowX = {-baseW/4, 0, (int)(baseW*0.5)};
            int[] shadowY = {(int)(size*0.25), -peakH + (int)(size*0.1), (int)(size*0.25)};
            g2d.setColor(UIConfig.TERRAIN_MOUNTAIN_DARK);
            g2d.fillPolygon(shadowX, shadowY, 3);

            int[] px = {-baseW/2, 0, baseW/2};
            int[] py = {(int)(size*0.25), -peakH, (int)(size*0.25)};
            g2d.setColor(UIConfig.TERRAIN_MOUNTAIN_ROCK);
            g2d.fillPolygon(px, py, 3);

            int[] px2 = {-baseW/6, baseW/4, baseW*2/3};
            int[] py2 = {(int)(size*0.25), -(int)(peakH*0.65), (int)(size*0.25)};
            g2d.setColor(base);
            g2d.fillPolygon(px2, py2, 3);

            if (hex.hasResource(ResourceType.IRON)) {
                g2d.setColor(UIConfig.VISUAL_ORE_IRON);
                g2d.setStroke(new BasicStroke((float)(1.5 * zoom)));
                g2d.drawLine(-baseW/4, -(int)(peakH*0.3), 0, -(int)(peakH*0.5));
                g2d.drawLine(baseW/6, -(int)(peakH*0.2), baseW/3, -(int)(peakH*0.4));
                g2d.setStroke(new BasicStroke(1f));
            }

            boolean hasSnow = (season == Season.WINTER) || (zoom >= 1.25);
            if (hasSnow) {
                int[] snx = {-baseW/6, 0, baseW/6};
                int[] sny = {-peakH + (int)(peakH*0.38), -peakH, -peakH + (int)(peakH*0.38)};
                g2d.setColor(UIConfig.TERRAIN_MOUNTAIN_SNOW);
                g2d.fillPolygon(snx, sny, 3);
            }

            if (hex.hasResource(ResourceType.STONE)) {
                g2d.setColor(UIConfig.VISUAL_ORE_STONE);
                int br = (int)(5 * zoom);
                g2d.fillOval(-baseW/2 - br, (int)(size*0.1), br*2, br*2);
                g2d.fillOval(baseW/3, (int)(size*0.2), br*3, br*2);
            }
        }
    }

    private void drawMeadow(Graphics2D g2d, Hex hex, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_MEADOW;
        Color light = UIConfig.TERRAIN_MEADOW_LIGHT;
        Color dark  = UIConfig.TERRAIN_MEADOW_DARK;

        if (season == Season.WINTER) {
            base  = blendColor(base, new Color(185, 200, 215), 0.45f);
            light = blendColor(light, new Color(200, 215, 228), 0.45f);
        }

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 1.0) {
            if (hex.hasResource(ResourceType.FOOD)) {
                boolean isWheat = (hex.getResourceSubtype() == ResourceSubtype.WHEAT);
                Color cropColor = isWheat ? UIConfig.VISUAL_CROP_WHEAT : UIConfig.VISUAL_CROP_RICE;

                g2d.setColor(cropColor);
                g2d.setStroke(new BasicStroke((float)(2.0 * zoom)));
                int rows = 5;
                int rowH = (int)(size * 0.25 / rows);

                for (int row = -rows; row <= rows; row++) {
                    int ry = row * rowH * 2;
                    if (Math.abs(ry) < size * 0.6) {
                        g2d.drawLine(-(int)(size*0.45), ry, (int)(size*0.45), ry);
                    }
                }
                g2d.setStroke(new BasicStroke(1f));
            } else {
                int rows = 4;
                int rowH = (int)(size * 0.3 / rows);
                g2d.setColor(new Color(dark.getRed(), dark.getGreen(), dark.getBlue(), 60));
                g2d.setStroke(new BasicStroke((float)(0.7 * zoom)));
                for (int row = -rows; row <= rows; row++) {
                    int ry = row * rowH;
                    if (Math.abs(ry) < size * 0.7) {
                        g2d.drawLine(-(int)(size*0.55), ry, (int)(size*0.55), ry);
                    }
                }
                g2d.setStroke(new BasicStroke(1f));

                if (season == Season.SPRING) {
                    int[][] flowers = {{-(int)(size*0.2), -(int)(size*0.15)},
                            {(int)(size*0.1),  (int)(size*0.1)},
                            {-(int)(size*0.05),(int)(size*0.25)}};
                    g2d.setColor(UIConfig.TERRAIN_MEADOW_FLOWER);
                    for (int[] f : flowers) {
                        int fs = Math.max(2, (int)(3 * zoom));
                        g2d.fillOval(f[0]-fs/2, f[1]-fs/2, fs, fs);
                    }
                }
            }
        }
    }

    private void drawSea(Graphics2D g2d, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_SEA;
        Color light = UIConfig.TERRAIN_SEA_LIGHT;
        Color dark  = UIConfig.TERRAIN_SEA_DARK;

        if (season == Season.AUTUMN) {
            base  = base.darker();
            light = light.darker();
        }

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 0.75) {
            g2d.setColor(UIConfig.TERRAIN_SEA_FOAM);
            g2d.setStroke(new BasicStroke((float)(1.0 * zoom)));
            int waves = 3;
            for (int w = 0; w < waves; w++) {
                int wy = -size/3 + w * (size*2/waves/3);
                int ww = (int)(size * 0.45);
                g2d.drawArc(-ww, wy, ww, (int)(size*0.12), 0, 180);
                g2d.drawArc((int)(ww*0.1), wy + (int)(size*0.06),
                        (int)(ww*0.7), (int)(size*0.10), 0, 180);
            }
            g2d.setStroke(new BasicStroke(1f));
        }
    }

    private void drawMountainRange(Graphics2D g2d, int size, Season season, double zoom) {
        Color base  = UIConfig.TERRAIN_MTN_RANGE;
        Color light = UIConfig.TERRAIN_MTN_RANGE_LIGHT;
        Color dark  = new Color(30, 28, 34);

        fillHexGradient(g2d, size, light, base, dark);

        if (zoom >= 0.5) {
            int[] rx = new int[11];
            int[] ry = new int[11];
            int count = 0;
            int hw = (int)(size * 0.58);
            int bh = (int)(size * 0.22);
            int[] peaks = {0, -(int)(size*0.58), (int)(size*0.18), -(int)(size*0.48),
                    (int)(size*0.38), -(int)(size*0.40), (int)(size*0.58)};
            int[] peaky = {bh, -(int)(size*0.52), -(int)(size*0.28), -(int)(size*0.58),
                    -(int)(size*0.18), -(int)(size*0.45), bh};
            int numPts = peaks.length;
            rx = Arrays.copyOf(peaks, numPts);
            ry = Arrays.copyOf(peaky, numPts);

            g2d.setColor(UIConfig.TERRAIN_MTN_RANGE_PEAK);
            g2d.fillPolygon(rx, ry, numPts);

            int[] snx = {0, -size/10, size/10};
            int[] sny = {-(int)(size*0.52), -(int)(size*0.38), -(int)(size*0.38)};
            g2d.setColor(UIConfig.TERRAIN_MTN_RANGE_SNOW);
            g2d.fillPolygon(snx, sny, 3);

            if (zoom >= 1.0) {
                g2d.setColor(new Color(220, 50, 50, 140));
                g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, (int)(9 * zoom)));
                FontMetrics fm = g2d.getFontMetrics();
                String txt = "✕";
                g2d.drawString(txt, -fm.stringWidth(txt)/2, (int)(size*0.42));
            }
        }
    }

    private void fillHexGradient(Graphics2D g2d, int size,
                                 Color topLight, Color mid, Color bottomDark) {
        GradientPaint gp = new GradientPaint(
                -(int)(size * 0.5), -(int)(size * 0.55), topLight,
                (int)(size * 0.4),  (int)(size * 0.5),  bottomDark);
        g2d.setPaint(gp);
        g2d.fillPolygon(hxBase, hyBase, 6);
        g2d.setPaint(null);
    }

    private void drawTerritoryFill(Graphics2D g2d, Hex hex, int cx, int cy, int size) {
        g2d.translate(cx, cy);
        g2d.setColor(UIConfig.BORDER_TERRITORY_FILL);
        g2d.fillPolygon(hxBase, hyBase, 6);
        g2d.translate(-cx, -cy);
    }

    private static final int[][] HEX_DIR_VECTORS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    private void drawRivers(Graphics2D g2d, Hex hex, int cx, int cy, int size,
                            double zoom, GameMap map, GamePanel panel) {
        boolean hasAny = false;
        for (int d = 0; d < 6; d++) if (hex.hasRiver(d)) { hasAny = true; break; }
        if (!hasAny) return;

        for (int d = 0; d < 6; d++) {
            if (!hex.hasRiver(d)) continue;

            int v1x = hxBase[d], v1y = hyBase[d];
            int v2x = hxBase[(d + 1) % 6], v2y = hyBase[(d + 1) % 6];
            int mx = (v1x + v2x) / 2, my = (v1y + v2y) / 2;

            float w = (float)(2.5 * zoom);

            g2d.setStroke(new BasicStroke(w + 1.5f * (float)zoom, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.RIVER_DARK);
            g2d.drawLine(cx, cy, cx + mx, cy + my);

            g2d.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.RIVER_COLOR);
            g2d.drawLine(cx, cy, cx + mx, cy + my);

            g2d.setStroke(new BasicStroke(w * 0.35f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.RIVER_SHINE);
            g2d.drawLine(cx + mx/4, cy + my/4, cx + mx*3/4, cy + my*3/4);
        }

        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawRoad(Graphics2D g2d, Hex hex, int cx, int cy, int size,
                          double zoom, GameMap map, GamePanel panel) {
        for (int d = 0; d < 6; d++) {
            int dq = HEX_DIR_VECTORS[d][0], dr = HEX_DIR_VECTORS[d][1];
            Hex neighbor = map.getHexAt(hex.getQ() + dq, hex.getR() + dr);
            if (neighbor == null || !neighbor.hasRoad() || !neighbor.isExplored()) continue;

            Point np = panel.getHexPixelCoords(neighbor.getQ(), neighbor.getR());

            float w = (float)(2.0 * zoom);

            g2d.setStroke(new BasicStroke(w + 2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.ROAD_SHADOW);
            g2d.drawLine(cx, cy, np.x, np.y);

            g2d.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.ROAD_COLOR);
            g2d.drawLine(cx, cy, np.x, np.y);

            if (zoom >= 1.0) {
                float[] dash = {(float)(5 * zoom), (float)(4 * zoom)};
                g2d.setStroke(new BasicStroke(w * 0.3f, BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_ROUND, 0, dash, 0));
                g2d.setColor(UIConfig.ROAD_BORDER);
                g2d.drawLine(cx, cy, np.x, np.y);
            }
        }
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawWalls(Graphics2D g2d, Hex hex, int cx, int cy, int size,
                           double zoom, GameMap map, GamePanel panel) {
        boolean hasAny = false;
        for (int d = 0; d < 6; d++) if (hex.hasWall(d)) { hasAny = true; break; }
        if (!hasAny) return;

        for (int d = 0; d < 6; d++) {
            if (!hex.hasWall(d)) continue;

            int v1x = hxBase[d] + cx, v1y = hyBase[d] + cy;
            int v2x = hxBase[(d+1)%6] + cx, v2y = hyBase[(d+1)%6] + cy;

            float w = (float)(4.5 * zoom);

            g2d.setStroke(new BasicStroke(w + 2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g2d.setColor(UIConfig.WALL_SHADOW);
            g2d.drawLine(v1x + 1, v1y + 1, v2x + 1, v2y + 1);

            g2d.setStroke(new BasicStroke(w, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g2d.setColor(UIConfig.WALL_STONE);
            g2d.drawLine(v1x, v1y, v2x, v2y);

            g2d.setStroke(new BasicStroke(w * 0.3f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER));
            g2d.setColor(UIConfig.WALL_HIGHLIGHT);
            g2d.drawLine(v1x - 1, v1y - 1, v2x - 1, v2y - 1);

            if (zoom >= 1.0) {
                drawBattlements(g2d, v1x, v1y, v2x, v2y, zoom);
            }
        }

        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawBattlements(Graphics2D g2d, int x1, int y1, int x2, int y2, double zoom) {
        double len = Math.sqrt((x2-x1)*(double)(x2-x1) + (y2-y1)*(double)(y2-y1));
        if (len < 4) return;

        double dx = (x2 - x1) / len;
        double dy = (y2 - y1) / len;
        double nx = -dy, ny = dx;

        int count = (int)(len / (6.5 * zoom));
        count = Math.max(2, Math.min(count, 7));

        int bh = (int)(4 * zoom);
        int bw = (int)(3 * zoom);

        g2d.setColor(UIConfig.WALL_STONE);
        for (int i = 0; i <= count; i++) {
            double t = (double) i / count;
            int bx = (int)(x1 + dx * len * t);
            int by = (int)(y1 + dy * len * t);
            int[] bsx = {(int)(bx + nx*2), (int)(bx + nx*2 + dx*bw),
                    (int)(bx + nx*(2+bh) + dx*bw), (int)(bx + nx*(2+bh))};
            int[] bsy = {(int)(by + ny*2), (int)(by + ny*2 + dy*bw),
                    (int)(by + ny*(2+bh) + dy*bw), (int)(by + ny*(2+bh))};
            g2d.fillPolygon(bsx, bsy, 4);
        }
    }

    private void drawTerritoryBorder(Graphics2D g2d, Hex hex, int cx, int cy, int size,
                                     double zoom, GameMap map, GamePanel panel) {
        for (int d = 0; d < 6; d++) {
            int dq = HEX_DIR_VECTORS[d][0], dr = HEX_DIR_VECTORS[d][1];
            Hex neighbor = map.getHexAt(hex.getQ() + dq, hex.getR() + dr);
            boolean neighborOwned = (neighbor != null && neighbor.isInsideBorder());
            if (neighborOwned) continue;

            int v1x = hxBase[d] + cx, v1y = hyBase[d] + cy;
            int v2x = hxBase[(d+1)%6] + cx, v2y = hyBase[(d+1)%6] + cy;

            float w = (float)(2.0 * zoom);

            g2d.setStroke(new BasicStroke(w + 3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.BORDER_GLOW);
            g2d.drawLine(v1x, v1y, v2x, v2y);

            g2d.setStroke(new BasicStroke(w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2d.setColor(UIConfig.BORDER_TERRITORY);
            g2d.drawLine(v1x, v1y, v2x, v2y);
        }
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawBuilding(Graphics2D g2d, Hex hex, Building building,
                              int cx, int cy, int size, double zoom) {
        if (zoom < 0.5) return;

        BuildingType type = building.getType();
        int bsize = (int)(size * 0.42);

        g2d.translate(cx, cy);

        g2d.setColor(new Color(0, 0, 0, 100));
        drawBuildingShape(g2d, type, bsize + 2, 2);

        Color buildColor = getBuildingColor(type);
        g2d.setColor(buildColor);
        drawBuildingShape(g2d, type, bsize, 0);

        if (building.getHp() < building.getMaxHp() && zoom >= 0.75) {
            drawHPBar(g2d, building.getHp(), building.getMaxHp(), bsize, zoom);
        }

        if (zoom >= 1.5) {
            String symbol = getBuildingSymbol(type);
            int fs = (int)(8 * zoom);
            g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, fs));
            g2d.setColor(Color.WHITE);
            FontMetrics fm = g2d.getFontMetrics();
            g2d.drawString(symbol, -fm.stringWidth(symbol)/2, fm.getAscent()/2);
        }

        g2d.translate(-cx, -cy);
    }

    private void drawBuildingShape(Graphics2D g2d, BuildingType type, int s, int offset) {
        int ox = offset, oy = offset;
        switch (type) {
            case TOWN_HALL -> {
                g2d.fillRect(-s + ox, -(int)(s*0.7) + oy, s*2, (int)(s*1.4));
                g2d.fillRect(-(int)(s*0.35) + ox, -(int)(s*1.1) + oy, (int)(s*0.7), (int)(s*0.4));
                g2d.fillRect(-s + ox, -(int)(s*0.9) + oy, (int)(s*0.4), (int)(s*0.2));
                g2d.fillRect((int)(s*0.6) + ox, -(int)(s*0.9) + oy, (int)(s*0.4), (int)(s*0.2));
            }
            case LUMBER_MILL -> {
                g2d.fillOval(-s + ox, -s + oy, s*2, s*2);
                g2d.setColor(g2d.getColor().darker());
                g2d.fillOval(-(int)(s*0.45) + ox, -(int)(s*0.45) + oy,
                        (int)(s*0.9), (int)(s*0.9));
            }
            case FARM -> {
                g2d.fillRect(-s + ox, -(int)(s*0.7) + oy, s*2, (int)(s*1.4));
                g2d.setColor(g2d.getColor().darker());
                g2d.drawRect(-(int)(s*0.5) + ox, -(int)(s*0.3) + oy,
                        (int)(s*1.0), (int)(s*0.6));
            }
            case STONE_MINE, IRON_MINE -> {
                int[] dx = {0, -s, 0, s};
                int[] dy = {-(int)(s*0.8), 0, (int)(s*0.8), 0};
                for (int i = 0; i < 4; i++) { dx[i] += ox; dy[i] += oy; }
                g2d.fillPolygon(dx, dy, 4);
            }
            case STABLE -> {
                g2d.fillRect(-s + ox, -(int)(s*0.4) + oy, s*2, (int)(s*1.1));
                int[] rX = {-s + ox, 0 + ox, s + ox};
                int[] rY = {-(int)(s*0.4) + oy, -(int)(s*1.0) + oy, -(int)(s*0.4) + oy};
                g2d.fillPolygon(rX, rY, 3);
            }
            case SETTLEMENT -> {
                g2d.fillRect(-(int)(s*0.55) + ox, -(int)(s*0.3) + oy,
                        (int)(s*1.1), (int)(s*1.0));
                int[] hRx = {-(int)(s*0.55) + ox, 0 + ox, (int)(s*0.55) + ox};
                int[] hRy = {-(int)(s*0.3) + oy, -(int)(s*0.95) + oy, -(int)(s*0.3) + oy};
                g2d.fillPolygon(hRx, hRy, 3);
                g2d.setColor(g2d.getColor().brighter());
                g2d.fillRect((int)(s*0.4) + ox, (int)(s*0.1) + oy,
                        (int)(s*0.55), (int)(s*0.65));
            }
            case DOCK -> {
                g2d.fillRect(-(int)(s*0.15) + ox, -(int)(s*0.85) + oy,
                        (int)(s*0.3), (int)(s*1.7));
                g2d.fillRect(-s + ox, -(int)(s*0.65) + oy,
                        s*2, (int)(s*0.28));
                g2d.drawArc(-(int)(s*0.65) + ox, (int)(s*0.15) + oy,
                        (int)(s*1.3), (int)(s*0.8), 0, -180);
            }
            case MONUMENT -> {
                g2d.fillRect(-(int)(s*0.22) + ox, -(int)(s*1.05) + oy,
                        (int)(s*0.44), (int)(s*1.75));
                g2d.fillRect(-s + ox, (int)(s*0.6) + oy, s*2, (int)(s*0.12));
                int[] mx = {-(int)(s*0.22) + ox, 0 + ox, (int)(s*0.22) + ox};
                int[] my = {-(int)(s*1.05) + oy, -(int)(s*1.45) + oy, -(int)(s*1.05) + oy};
                g2d.fillPolygon(mx, my, 3);
            }
            case BAZAAR -> {
                g2d.fillRect(-s + ox, -(int)(s*0.05) + oy, s*2, (int)(s*0.75));
                int[] awX = {-s + ox, 0 + ox, s + ox};
                int[] awY = {-(int)(s*0.05) + oy, -(int)(s*0.65) + oy, -(int)(s*0.05) + oy};
                g2d.fillPolygon(awX, awY, 3);
            }
            case TRADING_POST -> {
                g2d.fillRect(-(int)(s*0.1) + ox, -(int)(s*0.9) + oy,
                        (int)(s*0.2), (int)(s*1.6));
                g2d.fillRect(-s + ox, -(int)(s*0.75) + oy, s*2, (int)(s*0.18));
                g2d.fillOval(-(int)(s*0.55) + ox, -(int)(s*0.65) + oy,
                        (int)(s*0.6), (int)(s*0.6));
                g2d.fillOval((int)(s*0.0) + ox, -(int)(s*0.45) + oy,
                        (int)(s*0.6), (int)(s*0.6));
            }
            case TRIBE_CAMP -> {
                int[] ttx = {-s + ox, 0 + ox, s + ox};
                int[] tty = {(int)(s*0.65) + oy, -(int)(s*0.95) + oy, (int)(s*0.65) + oy};
                g2d.fillPolygon(ttx, tty, 3);
                g2d.setColor(g2d.getColor().darker().darker());
                int[] etx = {-(int)(s*0.22) + ox, 0 + ox, (int)(s*0.22) + ox};
                int[] ety = {(int)(s*0.65) + oy, (int)(s*0.0) + oy, (int)(s*0.65) + oy};
                g2d.fillPolygon(etx, ety, 3);
            }
            // اصلاح فاز 2: گرافیک اختصاصی Outpost (برجک دیده‌بانی)
            case OUTPOST -> {
                // برجک پایه چوبی
                g2d.fillRect(-(int)(s*0.35) + ox, -(int)(s*0.8) + oy, (int)(s*0.7), (int)(s*1.6));
                // بالکن چوبی
                g2d.fillRect(-(int)(s*0.5) + ox, -(int)(s*0.8) + oy, (int)(s*1.0), (int)(s*0.3));
                // پرچم بالا
                g2d.setColor(UIConfig.UNIT_SWORDSMAN);
                int[] px = {ox, ox + (int)(s*0.6), ox};
                int[] py = {-(int)(s*0.8) + oy, -(int)(s*0.6) + oy, -(int)(s*0.4) + oy};
                g2d.fillPolygon(px, py, 3);
            }
            default -> {
                g2d.fillRect(-(int)(s*0.65) + ox, -(int)(s*0.65) + oy,
                        (int)(s*1.3), (int)(s*1.3));
            }
        }
    }

    private Color getBuildingColor(BuildingType type) {
        return switch (type) {
            case TOWN_HALL    -> UIConfig.BUILDING_TOWN_HALL;
            case LUMBER_MILL  -> UIConfig.BUILDING_LUMBER_MILL;
            case FARM         -> UIConfig.BUILDING_FARM;
            case STONE_MINE   -> UIConfig.BUILDING_STONE_MINE;
            case IRON_MINE    -> UIConfig.BUILDING_IRON_MINE;
            case STABLE       -> UIConfig.BUILDING_STABLE;
            case SETTLEMENT   -> UIConfig.BUILDING_SETTLEMENT;
            case DOCK         -> UIConfig.BUILDING_DOCK;
            case MONUMENT     -> UIConfig.BUILDING_MONUMENT;
            case BAZAAR       -> UIConfig.BUILDING_BAZAAR;
            case TRADING_POST -> UIConfig.BUILDING_TRADING_POST;
            case TRIBE_CAMP   -> UIConfig.BUILDING_TRIBE_CAMP;
            case OUTPOST      -> UIConfig.BUILDING_OUTPOST;
            default           -> Color.GRAY;
        };
    }

    private String getBuildingSymbol(BuildingType type) {
        return switch (type) {
            case TOWN_HALL    -> "🏰";
            case LUMBER_MILL  -> "🌲";
            case FARM         -> "🌾";
            case STONE_MINE   -> "⛏️";
            case IRON_MINE    -> "🔩";
            case STABLE       -> "🐎";
            case SETTLEMENT   -> "🏘️";
            case DOCK         -> "⚓";
            case MONUMENT     -> "🗿";
            case BAZAAR       -> "⚖️";
            case TRADING_POST -> "🏪";
            case TRIBE_CAMP   -> "⛺";
            case OUTPOST      -> "🗼";
            default           -> "?";
        };
    }

    private void drawHPBar(Graphics2D g2d, int hp, int maxHp, int bsize, double zoom) {
        int bw = (int)(bsize * 1.8);
        int bh = (int)(3 * zoom);
        int by = (int)(bsize * 0.85);
        float pct = (float) hp / maxHp;

        g2d.setColor(new Color(30, 30, 30, 200));
        g2d.fillRoundRect(-bw/2, by, bw, bh, 2, 2);

        Color hpColor = pct > 0.6f ? new Color(60, 200, 80)
                : pct > 0.3f ? new Color(230, 180, 30)
                : new Color(220, 55, 55);
        g2d.setColor(hpColor);
        g2d.fillRoundRect(-bw/2, by, (int)(bw * pct), bh, 2, 2);

        g2d.setColor(new Color(0, 0, 0, 180));
        g2d.setStroke(new BasicStroke(0.5f));
        g2d.drawRoundRect(-bw/2, by, bw, bh, 2, 2);
        g2d.setStroke(new BasicStroke(1f));
    }

    // ─── Pass 8: Resource Icons ───────────────────────────────────────────────

    private void drawResourceIcons(Graphics2D g2d, Hex hex, int cx, int cy,
                                   int size, double zoom) {
        if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed() && zoom < 2.0) return;

        ResourceType res = null;
        Color resColor = Color.WHITE;

        if (hex.hasResource(ResourceType.FOOD)) {
            res = ResourceType.FOOD;
            resColor = getResourceColor(hex);
        } else if (hex.hasResource(ResourceType.WOOD)) {
            res = ResourceType.WOOD;
            resColor = UIConfig.RESOURCE_WOOD;
        } else if (hex.hasResource(ResourceType.STONE)) {
            res = ResourceType.STONE;
            resColor = UIConfig.RESOURCE_STONE;
        } else if (hex.hasResource(ResourceType.IRON)) {
            res = ResourceType.IRON;
            resColor = UIConfig.RESOURCE_IRON;
        }

        if (res == null) return;

        int rs = (int)(size * 0.28);
        int rx = cx + (int)(size * 0.42);
        int ry = cy - (int)(size * 0.42);

        g2d.translate(rx, ry);

        g2d.setColor(new Color(0, 0, 0, 170));
        g2d.fillOval(-rs - 2, -rs - 2, (rs + 2) * 2, (rs + 2) * 2);

        g2d.setColor(resColor);
        g2d.fillOval(-rs, -rs, rs*2, rs*2);

        g2d.setColor(Color.WHITE);
        g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.BOLD, (int)(rs * 1.1)));
        String sym = getResourceSymbol(res, hex.getResourceSubtype());
        FontMetrics fm = g2d.getFontMetrics();
        g2d.drawString(sym, -fm.stringWidth(sym)/2, fm.getAscent()/2 - 1);

        g2d.translate(-rx, -ry);
    }

    private Color getResourceColor(Hex hex) {
        ResourceSubtype sub = hex.getResourceSubtype();
        if (sub == ResourceSubtype.FISH)   return UIConfig.RESOURCE_FISH;
        if (sub == ResourceSubtype.WHEAT)  return UIConfig.RESOURCE_WHEAT;
        if (sub == ResourceSubtype.RICE)   return UIConfig.TERRAIN_MEADOW;
        if (sub == ResourceSubtype.CATTLE) return UIConfig.RESOURCE_CATTLE;
        if (sub == ResourceSubtype.SHEEP)  return new Color(230, 228, 225);
        return UIConfig.RESOURCE_FOOD;
    }

    private String getResourceSymbol(ResourceType type, ResourceSubtype sub) {
        return switch (type) {
            case FOOD -> switch (sub) {
                case WHEAT  -> "W";
                case RICE   -> "R";
                case CATTLE -> "C";
                case SHEEP  -> "S";
                case FISH   -> "F";
                default     -> "F";
            };
            case WOOD  -> "W";
            case STONE -> "S";
            case IRON  -> "I";
            default    -> "?";
        };
    }

    // ─── Pass 9: Highlights ───────────────────────────────────────────────────

    private void drawHighlights(Graphics2D g2d, GamePanel panel, GameMap map,
                                UnitController unitController, Rectangle clip,
                                int size, double zoom) {
        Unit selected = panel.getSelectedUnit();
        if (selected == null) return;

        Point selPt = panel.getHexPixelCoords(selected.getQ(), selected.getR());

        double pulse = panel.getPulseScale();
        int ringSize = (int)(size * pulse);
        g2d.setColor(UIConfig.HEX_SELECTED_GLOW);
        g2d.setStroke(new BasicStroke((float)(3.5 * zoom * pulse)));
        g2d.translate(selPt.x, selPt.y);
        scaleAndDrawPolygon(g2d, hxBase, hyBase, 6, pulse);
        g2d.translate(-selPt.x, -selPt.y);

        g2d.setColor(UIConfig.HEX_SELECTED);
        g2d.setStroke(new BasicStroke((float)(2.5 * zoom)));
        g2d.translate(selPt.x, selPt.y);
        g2d.drawPolygon(hxBase, hyBase, 6);
        g2d.translate(-selPt.x, -selPt.y);
        g2d.setStroke(new BasicStroke(1f));

        for (Hex hex : map.getHexes()) {
            if (!hex.isExplored()) continue;
            Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
            if (!clip.contains(pt)) continue;

            int dist = map.getHexDistance(selected.getQ(), selected.getR(),
                    hex.getQ(), hex.getR());
            if (dist == 0 || dist > 2) continue;

            boolean canMove = unitController.canMove(selected, hex);
            boolean isMilitary = (selected.getAttackRange() > 0);

            if (canMove) {
                drawHexHighlight(g2d, pt.x, pt.y, UIConfig.HEX_MOVE_FILL, UIConfig.HEX_MOVE_TARGET, zoom);
            } else if (isMilitary && dist <= selected.getAttackRange()) {
                drawHexHighlight(g2d, pt.x, pt.y, UIConfig.HEX_ATTACK_FILL, UIConfig.HEX_ATTACK_TARGET, zoom);
            }
        }
    }

    private void drawHexHighlight(Graphics2D g2d, int cx, int cy,
                                  Color fill, Color border, double zoom) {
        g2d.translate(cx, cy);
        g2d.setColor(fill);
        g2d.fillPolygon(hxBase, hyBase, 6);
        g2d.setColor(border);
        g2d.setStroke(new BasicStroke((float)(2.0 * zoom)));
        g2d.drawPolygon(hxBase, hyBase, 6);
        g2d.setStroke(new BasicStroke(1f));
        g2d.translate(-cx, -cy);
    }

    // ─── Pass 10: Disaster Overlays ───────────────────────────────────────────

    private void drawDisasterOverlays(Graphics2D g2d, GamePanel panel,
                                      Rectangle clip, int size) {

        List<Hex> floodHexes = panel.getFloodedHexes();
        float floodAlpha = panel.getFloodAlpha();
        if (!floodHexes.isEmpty() && floodAlpha > 0) {
            for (Hex hex : floodHexes) {
                if (!hex.isVisible()) continue;
                Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
                if (!clip.contains(pt)) continue;

                g2d.translate(pt.x, pt.y);

                g2d.setColor(new Color(30, 100, 200, (int)(floodAlpha * 210)));
                g2d.fillPolygon(hxBase, hyBase, 6);

                g2d.setColor(new Color(120, 190, 255, (int)(floodAlpha * 150)));
                g2d.setStroke(new BasicStroke((float)(1.5 * panel.getZoomFactor())));
                long time = System.currentTimeMillis();
                int offset1 = (int)(Math.sin(time / 300.0 + hex.getQ()) * size * 0.1);
                int offset2 = (int)(Math.cos(time / 400.0 + hex.getR()) * size * 0.15);

                g2d.drawLine(-size/2, -size/4 + offset1, size/2, -size/4 + offset1);
                g2d.drawLine(-size/3, size/4 + offset2, size/3, size/4 + offset2);

                g2d.setStroke(new BasicStroke(1f));
                g2d.translate(-pt.x, -pt.y);
            }
        }

        List<Hex> eqHexes = panel.getEarthquakeHexes();
        int eqTimer = panel.getEarthquakeTimer();
        if (!eqHexes.isEmpty() && eqTimer > 0) {
            float crackAlpha = Math.min(1.0f, eqTimer / 20.0f);
            g2d.setColor(new Color(20, 10, 5, (int)(crackAlpha * 200)));
            g2d.setStroke(new BasicStroke((float)(2.5 * panel.getZoomFactor()), BasicStroke.CAP_ROUND, BasicStroke.JOIN_MITER));

            for (Hex hex : eqHexes) {
                if (!hex.isVisible()) continue;
                Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
                if (!clip.contains(pt)) continue;

                g2d.translate(pt.x, pt.y);
                int s = (int)(size * 0.5);
                g2d.drawLine(-s/2, -s/2, -s/4, -s/8);
                g2d.drawLine(-s/4, -s/8, s/6, 0);
                g2d.drawLine(s/6, 0, s/3, s/4);
                g2d.drawLine(s/3, s/4, s/2, s/2);

                g2d.drawLine(s/6, 0, s/4, -s/3);
                g2d.translate(-pt.x, -pt.y);
            }
            g2d.setStroke(new BasicStroke(1f));
        }

        List<Hex> bearHexes = panel.getBearAttackHexes();
        float bearAlpha = panel.getBearAlpha();
        if (!bearHexes.isEmpty() && bearAlpha > 0) {
            for (Hex hex : bearHexes) {
                if (!hex.isVisible()) continue;
                Point pt = panel.getHexPixelCoords(hex.getQ(), hex.getR());
                if (!clip.contains(pt)) continue;

                g2d.translate(pt.x, pt.y);
                g2d.setColor(new Color(140, 70, 20, (int)(bearAlpha * 200)));
                g2d.fillPolygon(hxBase, hyBase, 6);
                g2d.setColor(new Color(220, 140, 40, (int)(bearAlpha * 100)));
                g2d.fillPolygon(hxInner, hyInner, 6);
                g2d.translate(-pt.x, -pt.y);
            }
        }
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private void drawHexAt(Graphics2D g2d, int cx, int cy, boolean fill) {
        g2d.translate(cx, cy);
        if (fill) g2d.fillPolygon(hxBase, hyBase, 6);
        else g2d.drawPolygon(hxBase, hyBase, 6);
        g2d.translate(-cx, -cy);
    }

    private void scaleAndDrawPolygon(Graphics2D g2d, int[] xs, int[] ys, int n, double scale) {
        int[] sx = new int[n], sy = new int[n];
        for (int i = 0; i < n; i++) {
            sx[i] = (int)(xs[i] * scale);
            sy[i] = (int)(ys[i] * scale);
        }
        g2d.drawPolygon(sx, sy, n);
    }

    private Color blendColor(Color a, Color b, float t) {
        return new Color(
                (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int)(a.getBlue()  + (b.getBlue()  - a.getBlue())  * t));
    }

    // ─── UI Overlays (Hover Info) ────────────────────────────────────────────

    private void drawHexInfoOverlay(Graphics2D g2d, GamePanel panel, Hex hex) {
        if (hex == null || !hex.isExplored()) return;

        String terrainInfo = switch (hex.getTerrainType()) {
            case PLAINS         -> "Plains — 1 AP";
            case FOREST         -> "Forest — 2 AP";
            case MOUNTAIN       -> "Mountain — 4 AP | Can build Mine";
            case MOUNTAIN_RANGE -> "Mountain Range — ✕ IMPASSABLE";
            case MEADOW         -> "Meadow — 1 AP";
            case SEA            -> "Sea — requires Seafaring tech";
        };

        String resourceInfo = buildResourceString(hex);
        String borderInfo   = hex.isInsideBorder() ? "In Territory" : "Outside Territory";
        String roadInfo     = hex.hasRoad() ? " | 🛣 Road" : "";

        String line1 = terrainInfo + roadInfo;
        String line2 = resourceInfo.isEmpty() ? borderInfo : resourceInfo + " | " + borderInfo;

        drawOverlayBox(g2d, panel, line1, line2);
    }

    private String buildResourceString(Hex hex) {
        java.util.List<String> res = new java.util.ArrayList<>();
        if (hex.hasResource(ResourceType.FOOD)) {
            String sub = hex.getResourceSubtype() != ResourceSubtype.NONE
                    ? " (" + hex.getResourceSubtype().getDisplayName() + ")" : "";
            res.add("🍔 Food" + sub);
        }
        if (hex.hasResource(ResourceType.WOOD))  res.add("🪵 Wood");
        if (hex.hasResource(ResourceType.STONE)) res.add("🪨 Stone");
        if (hex.hasResource(ResourceType.IRON))  res.add("⚙️ Iron");

        return String.join(", ", res);
    }

    private void drawOverlayBox(Graphics2D g2d, GamePanel panel, String line1, String line2) {
        g2d.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        FontMetrics fm = g2d.getFontMetrics();

        int w1 = fm.stringWidth(line1);
        int w2 = fm.stringWidth(line2);
        int boxW = Math.max(w1, w2) + 30;
        int boxH = 60;

        int x = 20;
        int y = panel.getHeight() - boxH - 20;

        // Shadow
        g2d.setColor(new Color(0, 0, 0, 150));
        g2d.fillRoundRect(x + 4, y + 4, boxW, boxH, 12, 12);

        // Background
        g2d.setColor(new Color(25, 28, 35, 230));
        g2d.fillRoundRect(x, y, boxW, boxH, 12, 12);

        // Border (Accent glow)
        g2d.setColor(new Color(65, 165, 255, 180));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(x, y, boxW, boxH, 12, 12);
        g2d.setStroke(new BasicStroke(1f));

        // Text Line 1
        g2d.setColor(Color.WHITE);
        g2d.drawString(line1, x + 15, y + 25);

        // Text Line 2
        g2d.setColor(new Color(180, 190, 200));
        g2d.setFont(new Font(UIConfig.FONT_SANS_SERIF, Font.PLAIN, 12));
        g2d.drawString(line2, x + 15, y + 45);
    }
}