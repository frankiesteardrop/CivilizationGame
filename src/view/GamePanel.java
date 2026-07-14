package view;

import controller.MainController;
import model.Hex;
import model.Unit;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class GamePanel extends JPanel {

    private final MainController mainController;

    public static final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    public static final int HEX_SIZE = 40;

    private int zoomIndex = 2;
    private double zoomFactor = ZOOM_LEVELS[zoomIndex];
    private int offsetX = 400;
    private int offsetY = 300;

    private Unit selectedUnit = null;
    private Unit animatingUnit = null;
    private double animProgress = 0.0;
    private int animStartX, animStartY, animTargetX, animTargetY;
    private int animTargetQ, animTargetR;

    private Hex hoveredHex = null;
    private double pulseScale = 1.0;
    private boolean pulseGrowing = true;

    private final Timer animationTimer;
    private final HexRenderer hexRenderer;
    private final UnitRenderer unitRenderer;

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
        setBackground(new Color(15, 18, 22));
        setFocusable(true);

        this.hexRenderer = new HexRenderer();
        this.unitRenderer = new UnitRenderer();

        GameInputHandler inputHandler = new GameInputHandler(this, mainController);
        addMouseListener(inputHandler);
        addMouseMotionListener(inputHandler);
        addMouseWheelListener(inputHandler);

        animationTimer = new Timer(16, e -> {
            boolean needsRepaint = false;

            if (animatingUnit != null) {
                updateAnimation();
                needsRepaint = true;
            }

            if (selectedUnit != null) {
                updatePulseEffect();
                needsRepaint = true;
            }

            if (needsRepaint) {
                repaint();
            }
        });
        animationTimer.start();
    }

    private void updateAnimation() {
        if (animatingUnit == null) return;
        animProgress += 0.08;
        if (animProgress >= 1.0) {
            animProgress = 1.0;
            Hex targetHex = mainController.getGameMap().getHexAt(animTargetQ, animTargetR);
            mainController.getUnitController().executeMove(animatingUnit, targetHex, mainController.getGameMap());
            animatingUnit = null;
        }
    }

    private void updatePulseEffect() {
        if (pulseGrowing) {
            pulseScale += 0.015;
            if (pulseScale >= 1.25) pulseGrowing = false;
        } else {
            pulseScale -= 0.015;
            if (pulseScale <= 1.0) pulseGrowing = true;
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        hexRenderer.renderAll(g2d, this, mainController.getGameMap(), mainController.getUnitController());
        unitRenderer.renderAll(g2d, this, mainController.getGameMap());
    }

    public Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        return new Point((int)(x * zoomFactor) + offsetX, (int)(y * zoomFactor) + offsetY);
    }

    public Hex getHexAtPixel(Point p, List<Hex> hexes) {
        double rawX = (p.x - offsetX) / zoomFactor;
        double rawY = (p.y - offsetY) / zoomFactor;

        double qExact = (Math.sqrt(3.0)/3.0 * rawX - 1.0/3.0 * rawY) / HEX_SIZE;
        double rExact = (2.0/3.0 * rawY) / HEX_SIZE;

        int hexQ = (int) Math.round(qExact);
        int hexR = (int) Math.round(rExact);
        int hexS = -hexQ - hexR;

        double qDiff = Math.abs(hexQ - qExact);
        double rDiff = Math.abs(hexR - rExact);
        double sDiff = Math.abs(hexS - (-qExact - rExact));

        if (qDiff > rDiff && qDiff > sDiff) {
            hexQ = -hexR - hexS;
        } else if (rDiff > sDiff) {
            hexR = -hexQ - hexS;
        }

        for (Hex hex : hexes) {
            if (hex.getQ() == hexQ && hex.getR() == hexR) {
                return hex;
            }
        }
        return null;
    }

    public boolean isAnimating() { return animatingUnit != null; }
    public Unit getSelectedUnit() { return selectedUnit; }
    public void setSelectedUnit(Unit u) { this.selectedUnit = u; }
    public Unit getAnimatingUnit() { return animatingUnit; }
    public Hex getHoveredHex() { return hoveredHex; }
    public void setHoveredHex(Hex h) { this.hoveredHex = h; }
    public double getZoomFactor() { return zoomFactor; }
    public int getZoomIndex() { return zoomIndex; }
    public void setZoomIndex(int idx) { this.zoomIndex = idx; this.zoomFactor = ZOOM_LEVELS[idx]; }
    public int getOffsetX() { return offsetX; }
    public void setOffsetX(int x) { this.offsetX = x; }
    public int getOffsetY() { return offsetY; }
    public void setOffsetY(int y) { this.offsetY = y; }
    public double getPulseScale() { return pulseScale; }
    public double getAnimProgress() { return animProgress; }
    public int getAnimStartX() { return animStartX; }
    public int getAnimStartY() { return animStartY; }
    public int getAnimTargetX() { return animTargetX; }
    public int getAnimTargetY() { return animTargetY; }

    public void startAnimation(Unit unit, Hex targetHex, int startX, int startY, int targetX, int targetY) {
        this.animatingUnit = unit;
        this.animStartX = startX;
        this.animStartY = startY;
        this.animTargetX = targetX;
        this.animTargetY = targetY;
        this.animTargetQ = targetHex.getQ();
        this.animTargetR = targetHex.getR();
        this.animProgress = 0.0;
    }
}