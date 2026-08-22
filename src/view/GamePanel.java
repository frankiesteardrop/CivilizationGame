package view;

import controller.MainController;
import controller.MenuAction;
import model.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GamePanel extends JPanel implements GameEventListener {

    private final MainController mainController;

    public static final double[] ZOOM_LEVELS = {0.5, 0.75, 1.0, 1.25, 1.5, 2.0};
    public static final int HEX_SIZE = 40;

    private int    zoomIndex  = 2;
    private double zoomFactor = ZOOM_LEVELS[zoomIndex];
    private int    offsetX    = 400;
    private int    offsetY    = 300;

    private Unit   selectedUnit  = null;
    private Unit   animatingUnit = null;
    private double animProgress  = 0.0;
    private int    animStartX, animStartY, animTargetX, animTargetY;
    private int    animTargetQ,  animTargetR;

    private Hex    hoveredHex  = null;
    private double pulseScale  = 1.0;
    private boolean pulseGrowing = true;

    private int shakeDuration = 0;
    private int shakeX = 0, shakeY = 0;

    // [I7] Fix: متغیرهای مربوط به گرافیک بلایای طبیعی
    private List<Hex> earthquakeHexes = new ArrayList<>();
    private int       earthquakeTimer = 0;

    private List<Hex> floodedHexes    = new ArrayList<>();
    private float     floodAlpha      = 0f;

    private List<Hex> bearAttackHexes = new ArrayList<>();
    private float     bearAlpha       = 0f;
    private int       bearFlashTimer  = 0;

    private static class SeasonParticle {
        float x, y, speedX, speedY, size, alpha;
        SeasonParticle(float x, float y, float speedX, float speedY, float size, float alpha) {
            this.x = x; this.y = y; this.speedX = speedX; this.speedY = speedY;
            this.size = size; this.alpha = alpha;
        }
    }

    private final List<SeasonParticle> seasonParticles = new ArrayList<>();
    private Season lastParticleSeason = null;
    private final Random particleRandom = new Random();

    private final Timer        animationTimer;
    private final HexRenderer  hexRenderer;
    private final UnitRenderer unitRenderer;

    public GamePanel(MainController mainController) {
        this.mainController = mainController;
        setBackground(new Color(15, 18, 22));
        setFocusable(true);

        this.hexRenderer  = new HexRenderer();
        this.unitRenderer = new UnitRenderer();

        GameInputHandler inputHandler = new GameInputHandler(this, mainController);
        addMouseListener(inputHandler);
        addMouseMotionListener(inputHandler);
        addMouseWheelListener(inputHandler);

        GameEventDispatcher.addListener(this);

        animationTimer = new Timer(16, e -> {
            boolean needsRepaint = false;

            if (animatingUnit != null) { updateAnimation();    needsRepaint = true; }
            if (selectedUnit  != null) { updatePulseEffect();  needsRepaint = true; }

            // زلزله - لرزش صفحه
            if (shakeDuration > 0) {
                shakeX = (int)((Math.random() - 0.5) * 15);
                shakeY = (int)((Math.random() - 0.5) * 15);
                shakeDuration--;
                if (shakeDuration == 0) { shakeX = 0; shakeY = 0; }
                needsRepaint = true;
            }

            // [I7] Fix: زلزله - تایمر ترک‌های زمین
            if (earthquakeTimer > 0) {
                earthquakeTimer--;
                if (earthquakeTimer == 0) earthquakeHexes.clear();
                needsRepaint = true;
            }

            // سیل - بالا آمدن آب
            if (!floodedHexes.isEmpty() && floodAlpha < 0.6f) {
                floodAlpha += 0.02f;
                if (floodAlpha > 0.6f) floodAlpha = 0.6f;
                needsRepaint = true;
            } else if (!floodedHexes.isEmpty()) {
                // برای رندر امواج متحرک سیل، همیشه نیاز به ری‌پینت داریم
                needsRepaint = true;
            }

            if (bearFlashTimer > 0) {
                bearAlpha = Math.min(0.55f, bearAlpha + 0.04f);
                bearFlashTimer--;
                if (bearFlashTimer == 0) { bearAlpha = 0f; bearAttackHexes.clear(); }
                needsRepaint = true;
            }

            Season currentSeason = mainController.getGameMap().getCurrentSeason();
            if (currentSeason == Season.WINTER || currentSeason == Season.AUTUMN) {
                updateSeasonalParticles(currentSeason);
                needsRepaint = true;
            } else if (!seasonParticles.isEmpty()) {
                seasonParticles.clear();
                lastParticleSeason = null;
                needsRepaint = true;
            }

            if (needsRepaint) repaint();
        });
        animationTimer.start();

        // I4 (گام ۳): باز کردن Pause Menu با کلید Escape
        getInputMap(WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "openPauseMenu");
        getActionMap().put("openPauseMenu", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                openPauseMenu();
            }
        });
    }

    public void openPauseMenu() {
        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        boolean locked = isAnimating() || mainController.isProcessingTurn();
        PauseMenuDialog dialog = new PauseMenuDialog(parent, mainController, locked);
        dialog.setVisible(true);
        repaint();
    }

    private void updateSeasonalParticles(Season season) {
        if (season != lastParticleSeason) {
            seasonParticles.clear();
            lastParticleSeason = season;
        }

        int maxParticles = (season == Season.WINTER) ? 120 : 280;
        int panelW = Math.max(getWidth(), 100);
        int panelH = Math.max(getHeight(), 100);

        while (seasonParticles.size() < maxParticles) {
            boolean firstFrame = (lastParticleSeason == null);
            seasonParticles.add(createParticle(season, panelW, panelH, firstFrame));
        }

        for (SeasonParticle p : seasonParticles) {
            p.x += p.speedX;
            p.y += p.speedY;
        }

        seasonParticles.removeIf(p -> p.y > panelH + 20 || p.x < -30 || p.x > panelW + 30);
    }

    private SeasonParticle createParticle(Season season, int panelW, int panelH, boolean randomY) {
        float startX = particleRandom.nextFloat() * panelW;
        float startY = randomY ? particleRandom.nextFloat() * panelH : -particleRandom.nextFloat() * 20;

        if (season == Season.WINTER) {
            return new SeasonParticle(startX, startY,
                    -0.4f + particleRandom.nextFloat() * 0.8f,
                    0.8f  + particleRandom.nextFloat() * 1.5f,
                    2f + particleRandom.nextFloat() * 3f,
                    0.55f + particleRandom.nextFloat() * 0.45f);
        } else {
            return new SeasonParticle(startX, startY,
                    -2.5f - particleRandom.nextFloat() * 1.5f,
                    7f + particleRandom.nextFloat() * 5f,
                    1f,
                    0.25f + particleRandom.nextFloat() * 0.3f);
        }
    }

    private void drawSeasonalParticles(Graphics2D g2d) {
        if (seasonParticles.isEmpty()) return;

        Season season = mainController.getGameMap().getCurrentSeason();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        if (season == Season.WINTER) {
            for (SeasonParticle p : seasonParticles) {
                g2d.setColor(new Color(1f, 1f, 1f, Math.min(1f, p.alpha)));
                int sz = Math.max(1, (int) p.size);
                g2d.fillOval((int) p.x - sz / 2, (int) p.y - sz / 2, sz, sz);
            }
        } else if (season == Season.AUTUMN) {
            g2d.setStroke(new BasicStroke(1f));
            for (SeasonParticle p : seasonParticles) {
                g2d.setColor(new Color(0.55f, 0.72f, 0.90f, Math.min(1f, p.alpha)));
                int x1 = (int) p.x, y1 = (int) p.y;
                int x2 = (int)(p.x - 5), y2 = (int)(p.y - 12);
                g2d.drawLine(x1, y1, x2, y2);
            }
            g2d.setStroke(new BasicStroke(1f));
        }
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

        g2d.translate(shakeX, shakeY);
        hexRenderer.renderAll(g2d, this, mainController.getGameMap(), mainController.getUnitController());
        unitRenderer.renderAll(g2d, this, mainController.getGameMap());
        g2d.translate(-shakeX, -shakeY);

        drawSeasonalParticles(g2d);
    }

    public void showContextMenu(Point p, List<MenuAction> actions) {
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
                if (action.getDisabledReason() != null)
                    item.setToolTipText(action.getDisabledReason());
            } else {
                item.addActionListener(ev -> {
                    if (action.requiresConfirmation()) {
                        int confirm = JOptionPane.showConfirmDialog(this, action.getConfirmationMessage(),
                                "Confirmation", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (confirm == JOptionPane.YES_OPTION) {
                            action.execute();
                            setSelectedUnit(null);
                            repaint();
                        }
                    } else {
                        action.execute();
                        setSelectedUnit(null);
                        repaint();
                    }
                });
            }
            popup.add(item);
        }
        popup.show(this, p.x, p.y);
    }

    public void onTribeInteractionTriggered(Hex campHex) {
        if (campHex == null || !(campHex.getBuilding() instanceof TribeCamp)) return;
        if (campHex.getBuilding().isDestroyed()) return;

        TribeCamp camp = (TribeCamp) campHex.getBuilding();

        if (!camp.isDiscovered()) {
            GameEventDispatcher.fireNotification(
                    "⚠️ This tribe has not been discovered yet. Send a unit to explore the area.");
            return;
        }

        JFrame parent = (JFrame) SwingUtilities.getWindowAncestor(this);
        TribeInteractionDialog dialog = new TribeInteractionDialog(
                parent, camp, mainController, this::repaint);
        dialog.setVisible(true);
        repaint();
    }

    @Override
    public void onDisasterTriggered(String type, Hex center, List<Hex> affected) {
        SwingUtilities.invokeLater(() -> {
            if (center == null || !center.isVisible()) return;
            switch (type) {
                // [I7] Fix: ست کردن هکس‌های زلزله و شروع تایمر رسم ترک‌های زمین
                case "EARTHQUAKE"  -> {
                    shakeDuration = 30;
                    earthquakeHexes = new ArrayList<>(affected);
                    earthquakeTimer = 80;
                }
                case "FLOOD"       -> { floodedHexes = new ArrayList<>(affected); floodAlpha = 0f; }
                case "BEAR_ATTACK" -> {
                    bearAttackHexes = new ArrayList<>(affected);
                    bearAlpha = 0f;
                    bearFlashTimer = 40;
                }
            }
        });
    }

    @Override
    public void onCombatTriggered(List<Integer> atk, List<Integer> def, int atkDmg, int defDmg) {
        SwingUtilities.invokeLater(() -> new CombatVisualizerDialog(
                (JFrame) SwingUtilities.getWindowAncestor(this),
                atk, def, atkDmg, defDmg).setVisible(true));
    }

    public Point getHexPixelCoords(int q, int r) {
        double x = HEX_SIZE * Math.sqrt(3) * (q + r / 2.0);
        double y = HEX_SIZE * 3.0 / 2.0 * r;
        return new Point((int)(x * zoomFactor) + offsetX, (int)(y * zoomFactor) + offsetY);
    }

    public Hex getHexAtPixel(Point p, List<Hex> hexes) {
        double rawX   = (p.x - offsetX) / zoomFactor;
        double rawY   = (p.y - offsetY) / zoomFactor;
        double qExact = (Math.sqrt(3.0)/3.0 * rawX - 1.0/3.0 * rawY) / HEX_SIZE;
        double rExact = (2.0/3.0 * rawY) / HEX_SIZE;
        int hexQ = (int) Math.round(qExact);
        int hexR = (int) Math.round(rExact);
        int hexS = -hexQ - hexR;
        double qDiff = Math.abs(hexQ - qExact);
        double rDiff = Math.abs(hexR - rExact);
        double sDiff = Math.abs(hexS - (-qExact - rExact));
        if (qDiff > rDiff && qDiff > sDiff) hexQ = -hexR - hexS;
        else if (rDiff > sDiff)             hexR = -hexQ - hexS;
        return mainController.getGameMap().getHexAt(hexQ, hexR);
    }

    public boolean   isAnimating()       { return animatingUnit != null; }
    public Unit      getSelectedUnit()   { return selectedUnit; }
    public void      setSelectedUnit(Unit u) { this.selectedUnit = u; }
    public Unit      getAnimatingUnit()  { return animatingUnit; }
    public Hex       getHoveredHex()     { return hoveredHex; }
    public void      setHoveredHex(Hex h) { this.hoveredHex = h; }
    public double    getZoomFactor()     { return zoomFactor; }
    public int       getZoomIndex()      { return zoomIndex; }
    public void      setZoomIndex(int i) { this.zoomIndex = i; this.zoomFactor = ZOOM_LEVELS[i]; }
    public int       getOffsetX()        { return offsetX; }
    public void      setOffsetX(int x)   { this.offsetX = x; }
    public int       getOffsetY()        { return offsetY; }
    public void      setOffsetY(int y)   { this.offsetY = y; }
    public double    getPulseScale()     { return pulseScale; }
    public double    getAnimProgress()   { return animProgress; }
    public int       getAnimStartX()     { return animStartX; }
    public int       getAnimStartY()     { return animStartY; }
    public int       getAnimTargetX()    { return animTargetX; }
    public int       getAnimTargetY()    { return animTargetY; }
    public List<Hex> getFloodedHexes()   { return floodedHexes; }
    public float     getFloodAlpha()     { return floodAlpha; }
    public List<Hex> getBearAttackHexes(){ return bearAttackHexes; }
    public float     getBearAlpha()      { return bearAlpha; }

    // [I7] Fix: Getters برای دسترسی رندرر به هکس‌های زلزله‌زده
    public List<Hex> getEarthquakeHexes(){ return earthquakeHexes; }
    public int       getEarthquakeTimer(){ return earthquakeTimer; }

    public void startAnimation(Unit unit, Hex targetHex,
                               int startX, int startY, int targetX, int targetY) {
        this.animatingUnit = unit;
        this.animStartX    = startX;  this.animStartY  = startY;
        this.animTargetX   = targetX; this.animTargetY = targetY;
        this.animTargetQ   = targetHex.getQ();
        this.animTargetR   = targetHex.getR();
        this.animProgress  = 0.0;
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) {}
    @Override public void onUnitMoved(Unit unit, int oQ, int oR, int nQ, int nR) { repaint(); }
    @Override public void onUnitKilled(Unit unit)                                  { repaint(); }
    @Override public void onProductionCompleted(String itemName)                   {}
    @Override public void onTurnEnded(int newTurn) { floodedHexes.clear(); floodAlpha = 0f; repaint(); }
    @Override public void onStarvationChanged(boolean s)                           {}
    @Override public void onUnitStateChanged(Unit unit)                            { repaint(); }
    @Override public void onBuildingConstructed(Hex hex)                           { repaint(); }
    @Override public void onBuildingDestroyed(Hex hex)                             { repaint(); }
    @Override public void onBorderExpanded(int cq, int cr)                         { repaint(); }
    @Override public void onNotification(String message)                           {}
}