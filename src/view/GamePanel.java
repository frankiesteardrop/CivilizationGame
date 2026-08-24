package view;

import controller.MainController;
import controller.MenuAction;
import model.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * پنل اصلی بازی با موتور رندرینگ پیشرفته (Parallax Particle System و Cinematic VFX).
 */
public class GamePanel extends JPanel implements UnitListener, TurnListener, BuildingListener, MapListener, DisasterListener, CombatListener {

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

    private List<Hex> earthquakeHexes = new ArrayList<>();
    private int       earthquakeTimer = 0;

    private List<Hex> floodedHexes    = new ArrayList<>();
    private float     floodAlpha      = 0f;

    private List<Hex> bearAttackHexes = new ArrayList<>();
    private float     bearAlpha       = 0f;
    private int       bearFlashTimer  = 0;

    // ─── سیستم پارتیکل فیزیک‌محور پیشرفته (Advanced Particle System) ───
    private static class AdvancedParticle {
        float x, y, z;          // z برای عمق Parallax
        float speedX, speedY;
        float size, alpha;
        float phase, swing;     // برای رقص و آشفتگی سینوسی
        int type;               // 0: برف، 1: باران، 2: برگ پاییزی، 3: شکوفه بهاری
        Color color;

        AdvancedParticle(int type, float x, float y, float z, float spX, float spY, float sz, float a, float sw, Color c) {
            this.type = type; this.x = x; this.y = y; this.z = z;
            this.speedX = spX; this.speedY = spY; this.size = sz;
            this.alpha = a; this.swing = sw; this.color = c;
            this.phase = (float)(Math.random() * Math.PI * 2);
        }
    }

    private final List<AdvancedParticle> particles = new ArrayList<>();
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

            // افکت لرزش دوربین (زلزله) با کاهش شدت تدریجی
            if (shakeDuration > 0) {
                double intensity = (double) shakeDuration / 30.0;
                shakeX = (int)((Math.random() - 0.5) * 18 * intensity);
                shakeY = (int)((Math.random() - 0.5) * 18 * intensity);
                shakeDuration--;
                if (shakeDuration == 0) { shakeX = 0; shakeY = 0; }
                needsRepaint = true;
            }

            if (earthquakeTimer > 0) {
                earthquakeTimer--;
                if (earthquakeTimer == 0) earthquakeHexes.clear();
                needsRepaint = true;
            }

            if (!floodedHexes.isEmpty() && floodAlpha < 0.6f) {
                floodAlpha += 0.02f;
                if (floodAlpha > 0.6f) floodAlpha = 0.6f;
                needsRepaint = true;
            } else if (!floodedHexes.isEmpty()) {
                needsRepaint = true;
            }

            // افکت هاله خطر خرس (Red Vignette)
            if (bearFlashTimer > 0) {
                bearAlpha = Math.min(0.65f, bearAlpha + 0.05f);
                bearFlashTimer--;
                if (bearFlashTimer == 0) { bearAlpha = 0f; bearAttackHexes.clear(); }
                needsRepaint = true;
            } else if (bearAlpha > 0) {
                bearAlpha -= 0.02f;
                if (bearAlpha < 0) bearAlpha = 0;
                needsRepaint = true;
            }

            Season currentSeason = mainController.getGameMap().getCurrentSeason();
            updateAdvancedParticles(currentSeason);
            needsRepaint = true; // ذرات همیشه حرکت می‌کنند

            if (needsRepaint) repaint();
        });
        animationTimer.start();

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

    // ─── موتور تولید ذرات آب و هوا (Procedural Weather Engine) ───

    private void updateAdvancedParticles(Season season) {
        if (season != lastParticleSeason) {
            particles.clear();
            lastParticleSeason = season;
        }

        int maxParticles = switch (season) {
            case WINTER -> 250; // برف سنگین
            case AUTUMN -> 350; // باران و برگ
            case SPRING -> 80;  // شکوفه‌های کم‌تراکم
            default     -> 0;
        };

        int panelW = Math.max(getWidth(), 100);
        int panelH = Math.max(getHeight(), 100);

        // تولید ذرات جدید
        while (particles.size() < maxParticles) {
            boolean firstFrame = (lastParticleSeason == null);
            float startX = particleRandom.nextFloat() * (panelW + 200) - 100;
            float startY = firstFrame ? particleRandom.nextFloat() * panelH : -particleRandom.nextFloat() * 50;

            float z = 0.5f + particleRandom.nextFloat() * 1.0f; // Parallax Depth

            if (season == Season.WINTER) {
                // دانه برف
                float sz = (1.5f + particleRandom.nextFloat() * 3f) * z;
                float spY = (0.8f + particleRandom.nextFloat() * 1.2f) * z;
                particles.add(new AdvancedParticle(0, startX, startY, z, 0, spY, sz,
                        0.4f + particleRandom.nextFloat() * 0.4f, 1.5f * z, Color.WHITE));
            } else if (season == Season.AUTUMN) {
                if (particleRandom.nextFloat() > 0.15f) {
                    // باران پاییزی (سریع و زاویه‌دار)
                    float spY = (15.0f + particleRandom.nextFloat() * 10.0f) * z;
                    float spX = -3.0f - particleRandom.nextFloat() * 2.0f; // باد شدید به چپ
                    particles.add(new AdvancedParticle(1, startX, startY, z, spX, spY, 2.0f * z,
                            0.3f + particleRandom.nextFloat() * 0.3f, 0, new Color(150, 180, 210)));
                } else {
                    // برگ پاییزی
                    Color leafColor = particleRandom.nextBoolean() ? new Color(210, 100, 30) : new Color(180, 50, 20);
                    float spY = (1.5f + particleRandom.nextFloat() * 2.0f) * z;
                    particles.add(new AdvancedParticle(2, startX, startY, z, -2.0f * z, spY, 4.0f * z,
                            0.7f, 3.0f * z, leafColor));
                }
            } else if (season == Season.SPRING) {
                // شکوفه بهاری (آرام و رقصان)
                float spY = (0.5f + particleRandom.nextFloat() * 1.0f) * z;
                particles.add(new AdvancedParticle(3, startX, startY, z, 1.0f * z, spY, 3.5f * z,
                        0.6f, 2.0f * z, new Color(255, 180, 200)));
            }
        }

        // حرکت ذرات فیزیک‌محور
        for (AdvancedParticle p : particles) {
            p.phase += 0.05f;
            if (p.type == 0 || p.type == 2 || p.type == 3) {
                // رقص سینوسی برای برف، برگ و شکوفه
                p.x += p.speedX + Math.sin(p.phase) * p.swing;
            } else {
                // خط مستقیم برای باران
                p.x += p.speedX;
            }
            p.y += p.speedY;
        }

        // حذف ذرات خارج از کادر
        particles.removeIf(p -> p.y > panelH + 20 || p.x < -150 || p.x > panelW + 150);
    }

    private void drawAdvancedParticles(Graphics2D g2d) {
        if (particles.isEmpty()) return;

        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        for (AdvancedParticle p : particles) {
            g2d.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(p.alpha * 255)));

            if (p.type == 0 || p.type == 3) {
                // برف و شکوفه (دایره‌ای نرم)
                int sz = Math.max(2, (int) p.size);
                g2d.fillOval((int) p.x, (int) p.y, sz, sz);
            } else if (p.type == 1) {
                // باران (خطوط مورب)
                g2d.setStroke(new BasicStroke(p.size / 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int x2 = (int)(p.x - p.speedX * 1.2f);
                int y2 = (int)(p.y - p.speedY * 1.2f);
                g2d.drawLine((int)p.x, (int)p.y, x2, y2);
            } else if (p.type == 2) {
                // برگ پاییزی (چندضلعی کوچک)
                int sz = Math.max(3, (int) p.size);
                g2d.fillRect((int) p.x, (int) p.y, sz, sz - 1);
            }
        }
        g2d.setStroke(new BasicStroke(1f));
    }

    private void drawCinematicOverlays(Graphics2D g2d) {
        // افکت تپش خطر خرس (Red Vignette)
        if (bearAlpha > 0) {
            int w = getWidth();
            int h = getHeight();
            float radius = Math.max(w, h) * 0.8f;
            RadialGradientPaint rgp = new RadialGradientPaint(
                    w / 2f, h / 2f, radius,
                    new float[]{0.3f, 1.0f},
                    new Color[]{new Color(0, 0, 0, 0), new Color(180, 0, 0, (int)(bearAlpha * 255))}
            );
            g2d.setPaint(rgp);
            g2d.fillRect(0, 0, w, h);
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

        // اعمال لرزش زلزله روی نقشه بازی
        g2d.translate(shakeX, shakeY);
        hexRenderer.renderAll(g2d, this, mainController.getGameMap(), mainController.getUnitController());
        unitRenderer.renderAll(g2d, this, mainController.getGameMap());
        g2d.translate(-shakeX, -shakeY); // بازگشت برای رسم افکت‌های روی صفحه (Screen-Space)

        // رندر سیستم پارتیکل و افکت‌های سینمایی
        drawAdvancedParticles(g2d);
        drawCinematicOverlays(g2d);
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

    @Override public void onUnitMoved(Unit unit, int oQ, int oR, int nQ, int nR) { repaint(); }
    @Override public void onUnitKilled(Unit unit)                                  { repaint(); }
    @Override public void onUnitStateChanged(Unit unit)                            { repaint(); }
    @Override public void onTurnEnded(int newTurn) { floodedHexes.clear(); floodAlpha = 0f; repaint(); }
    @Override public void onStarvationChanged(boolean s)                           {}
    @Override public void onBuildingConstructed(Hex hex)                           { repaint(); }
    @Override public void onBuildingDestroyed(Hex hex)                             { repaint(); }
    @Override public void onBorderExpanded(int cq, int cr)                         { repaint(); }

    @Override
    public void onDisasterTriggered(String type, Hex center, List<Hex> affected) {
        SwingUtilities.invokeLater(() -> {
            if (center == null || !center.isVisible()) return;
            switch (type) {
                case "EARTHQUAKE"  -> {
                    shakeDuration = 45; // افزایش زمان لرزش
                    earthquakeHexes = new ArrayList<>(affected);
                    earthquakeTimer = 100;
                }
                case "FLOOD"       -> { floodedHexes = new ArrayList<>(affected); floodAlpha = 0f; }
                case "BEAR_ATTACK" -> {
                    bearAttackHexes = new ArrayList<>(affected);
                    bearAlpha = 0f;
                    bearFlashTimer = 60; // افزایش زمان تپش صفحه
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
}