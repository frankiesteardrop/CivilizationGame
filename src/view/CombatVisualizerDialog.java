package view;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.util.List;
import java.util.Random;

/**
 * Dialog نمایش بصری نبرد — تاس‌های متحرک با انیمیشن چرخش، رنگ‌بندی برد/باخت، و خلاصه آسیب.
 */
public class CombatVisualizerDialog extends JDialog {

    private static final Color BG_DARK    = new Color(18, 20, 26);
    private static final Color BG_FELT    = new Color(22, 55, 38);   // بافت میز نبرد
    private static final Color BG_CARD    = new Color(28, 33, 42);
    private static final Color ACCENT_RED = new Color(215, 52, 52);
    private static final Color ACCENT_BLU = new Color(52, 130, 215);
    private static final Color WIN_COLOR  = new Color(55, 215, 90);
    private static final Color LOSS_COLOR = new Color(215, 65, 65);
    private static final Color TEXT_MAIN  = new Color(225, 230, 240);
    private static final Color TEXT_DIM   = new Color(130, 140, 155);
    private static final Color GOLD       = new Color(220, 185, 45);

    private final List<Integer> attackerDice;
    private final List<Integer> defenderDice;
    private final int attackerDmg;
    private final int defenderDmg;

    // Animation
    private final Timer animTimer;
    private int animFrame = 0;
    private boolean animDone = false;
    private final int ANIM_FRAMES = 40;

    public CombatVisualizerDialog(JFrame parent, List<Integer> attackerDice,
                                  List<Integer> defenderDice,
                                  int attackerDmg, int defenderDmg) {
        super(parent, "⚔️ Battle Resolution", true);
        this.attackerDice = attackerDice;
        this.defenderDice = defenderDice;
        this.attackerDmg  = attackerDmg;
        this.defenderDmg  = defenderDmg;

        setUndecorated(true);
        setSize(600, 420);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = buildUI();
        setContentPane(root);

        // Start animation
        animTimer = new Timer(25, e -> {
            animFrame++;
            if (animFrame >= ANIM_FRAMES) {
                animDone = true;
                ((Timer) e.getSource()).stop();
            }
            root.repaint();
        });
        animTimer.start();
    }

    private JPanel buildUI() {
        JPanel panel = new JPanel(new BorderLayout(0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);

                // Background
                g2.setColor(BG_DARK);
                g2.fillRect(0, 0, getWidth(), getHeight());

                // Felt texture center band
                g2.setColor(BG_FELT);
                g2.fillRoundRect(20, 20, getWidth()-40, getHeight()-40, 18, 18);

                // Gold border
                g2.setColor(GOLD);
                g2.setStroke(new BasicStroke(2.5f));
                g2.drawRoundRect(20, 20, getWidth()-40, getHeight()-40, 18, 18);
                g2.setStroke(new BasicStroke(1f));

                // Header
                drawHeader(g2);

                // Dice area
                drawDiceArea(g2);

                // Result summary
                drawResultSummary(g2);
            }
        };

        // Close button
        JButton closeBtn = new JButton("✕  Close");
        styleCloseButton(closeBtn);
        closeBtn.addActionListener(e -> dispose());

        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 14));
        south.setOpaque(false);
        south.add(closeBtn);
        panel.add(south, BorderLayout.SOUTH);

        return panel;
    }

    private void drawHeader(Graphics2D g2) {
        g2.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 22));
        g2.setColor(GOLD);
        String title = "⚔  BATTLE  ⚔";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, (600 - fm.stringWidth(title)) / 2, 65);

        // Divider line
        g2.setColor(new Color(GOLD.getRed(), GOLD.getGreen(), GOLD.getBlue(), 120));
        g2.setStroke(new BasicStroke(1.5f));
        g2.drawLine(60, 78, 540, 78);
        g2.setStroke(new BasicStroke(1f));

        // Side labels
        g2.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 13));
        g2.setColor(ACCENT_RED);
        g2.drawString("🗡 ATTACKER", 75, 102);
        g2.setColor(ACCENT_BLU);
        FontMetrics fm2 = g2.getFontMetrics();
        String defLabel = "🛡 DEFENDER";
        g2.drawString(defLabel, 600 - 75 - fm2.stringWidth(defLabel), 102);
    }

    private void drawDiceArea(Graphics2D g2) {
        int centerY = 205;
        int dieW = 62, dieH = 62;

        // Attacker dice (left side)
        int atkCount = attackerDice.size();
        for (int i = 0; i < atkCount; i++) {
            int dx = 90 + i * (dieW + 18);
            int dy = centerY - dieH / 2;

            // Animated value (spinning before settling)
            int displayVal;
            if (!animDone && animFrame < ANIM_FRAMES - 5) {
                displayVal = new Random().nextInt(6) + 1;
            } else {
                displayVal = attackerDice.get(i);
            }

            boolean isWin = (i < defenderDice.size())
                    && (attackerDice.get(i) > defenderDice.get(i));
            drawDie(g2, dx, dy, dieW, dieH, displayVal, ACCENT_RED, isWin && animDone);
        }

        // VS divider
        g2.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 24));
        g2.setColor(GOLD);
        FontMetrics fm = g2.getFontMetrics();
        String vs = "VS";
        g2.drawString(vs, 300 - fm.stringWidth(vs)/2, centerY + fm.getAscent()/2 - 5);

        // Defender dice (right side)
        int defCount = defenderDice.size();
        for (int i = 0; i < defCount; i++) {
            int dx = 600 - 90 - (defCount - i) * (dieW + 18) + 18;
            int dy = centerY - dieH / 2;

            int displayVal;
            if (!animDone && animFrame < ANIM_FRAMES - 5) {
                displayVal = new Random().nextInt(6) + 1;
            } else {
                displayVal = defenderDice.get(i);
            }

            boolean isWin = (i < attackerDice.size())
                    && (defenderDice.get(i) >= attackerDice.get(i));
            drawDie(g2, dx, dy, dieW, dieH, displayVal, ACCENT_BLU, isWin && animDone);
        }

        // Comparison lines (after animation)
        if (animDone) {
            drawComparisonLines(g2, centerY, dieW, dieH, atkCount, defCount);
        }
    }

    private void drawDie(Graphics2D g2, int x, int y, int w, int h,
                         int value, Color accent, boolean isWinner) {
        // Glow for winner
        if (isWinner) {
            g2.setColor(new Color(WIN_COLOR.getRed(), WIN_COLOR.getGreen(), WIN_COLOR.getBlue(), 60));
            g2.fillRoundRect(x - 5, y - 5, w + 10, h + 10, 14, 14);
        }

        // Die shadow
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(x + 3, y + 3, w, h, 12, 12);

        // Die body gradient
        GradientPaint gp = new GradientPaint(
                x, y, new Color(58, 62, 75),
                x + w, y + h, new Color(32, 36, 46));
        g2.setPaint(gp);
        g2d_fillRoundRect(g2, x, y, w, h, 12);
        g2.setPaint(null);

        // Die border
        g2.setColor(isWinner ? WIN_COLOR : accent);
        g2.setStroke(new BasicStroke(isWinner ? 2.5f : 1.8f));
        g2.drawRoundRect(x, y, w, h, 12, 12);
        g2.setStroke(new BasicStroke(1f));

        // Inner bevel
        g2.setColor(new Color(255, 255, 255, 20));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 10, 10);
        g2.setStroke(new BasicStroke(1f));

        // Pip dots
        drawDiePips(g2, x, y, w, h, value, isWinner ? WIN_COLOR : Color.WHITE);

        // Value number below
        if (animDone) {
            g2.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 13));
            g2.setColor(isWinner ? WIN_COLOR : TEXT_DIM);
            FontMetrics fm = g2.getFontMetrics();
            String txt = String.valueOf(value);
            g2.drawString(txt, x + w/2 - fm.stringWidth(txt)/2, y + h + 18);
        }
    }

    private void drawDiePips(Graphics2D g2, int x, int y, int w, int h,
                             int value, Color pipColor) {
        g2.setColor(pipColor);
        int pipR = (int)(w * 0.09);
        int pad = (int)(w * 0.22);

        // Pip positions (normalized 0-1 → actual coordinates)
        int[][] pipLayouts = {
                {},
                {3, 3},                         // 1
                {1, 1, 5, 5},                   // 2
                {1, 1, 3, 3, 5, 5},             // 3
                {1, 1, 1, 5, 5, 1, 5, 5},       // 4
                {1, 1, 1, 5, 3, 3, 5, 1, 5, 5}, // 5
                {1, 1, 1, 3, 1, 5, 5, 1, 5, 3, 5, 5} // 6
        };

        if (value < 1 || value > 6) return;
        int[] layout = pipLayouts[value];

        // Grid: column 1=left, 3=center, 5=right; row same
        for (int i = 0; i < layout.length; i += 2) {
            int col = layout[i], row = layout[i+1];
            int px = x + pad + (col - 1) * (w - 2*pad) / 4;
            int py = y + pad + (row - 1) * (h - 2*pad) / 4;

            // Pip shadow
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillOval(px - pipR + 1, py - pipR + 1, pipR*2, pipR*2);

            // Pip
            g2.setColor(pipColor);
            g2.fillOval(px - pipR, py - pipR, pipR*2, pipR*2);

            // Pip highlight
            g2.setColor(new Color(255, 255, 255, 140));
            g2.fillOval(px - pipR + 1, py - pipR + 1, pipR, pipR);
        }
    }

    private void drawComparisonLines(Graphics2D g2, int centerY, int dieW, int dieH,
                                     int atkCount, int defCount) {
        int pairs = Math.min(atkCount, defCount);
        for (int i = 0; i < pairs; i++) {
            boolean atkWins = attackerDice.get(i) > defenderDice.get(i);

            int atkX = 90 + i * (dieW + 18) + dieW;
            int defX = 600 - 90 - (defCount - i - 1) * (dieW + 18) - 18;

            g2.setColor(atkWins
                    ? new Color(WIN_COLOR.getRed(), WIN_COLOR.getGreen(), WIN_COLOR.getBlue(), 120)
                    : new Color(LOSS_COLOR.getRed(), LOSS_COLOR.getGreen(), LOSS_COLOR.getBlue(), 120));
            g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g2.drawLine(atkX, centerY, defX, centerY);
            g2.setStroke(new BasicStroke(1f));
        }
    }

    private void drawResultSummary(Graphics2D g2) {
        int y = 320;

        // Divider
        g2.setColor(new Color(255, 255, 255, 30));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(60, y - 8, 540, y - 8);

        if (!animDone) return;

        // Attacker result
        if (attackerDmg > 0) {
            g2.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
            g2.setColor(LOSS_COLOR);
            g2.drawString("🗡 Attacker takes " + attackerDmg + " damage", 80, y + 20);
        }

        // Defender result
        if (defenderDmg > 0) {
            g2.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
            g2.setColor(WIN_COLOR);
            FontMetrics fm = g2.getFontMetrics();
            String defTxt = "🛡 Defender takes " + defenderDmg + " damage";
            g2.drawString(defTxt, 600 - 80 - fm.stringWidth(defTxt), y + 20);
        }

        // Summary badge
        String summary;
        Color summaryColor;
        if (defenderDmg > 0 && attackerDmg == 0) {
            summary = "DECISIVE VICTORY  🏆";
            summaryColor = WIN_COLOR;
        } else if (defenderDmg > attackerDmg) {
            summary = "VICTORY  ⚔";
            summaryColor = new Color(140, 230, 90);
        } else if (attackerDmg > defenderDmg) {
            summary = "DEFEAT  ✕";
            summaryColor = LOSS_COLOR;
        } else if (defenderDmg == 0 && attackerDmg == 0) {
            summary = "SIEGE DAMAGE  🏰";
            summaryColor = GOLD;
        } else {
            summary = "DRAW  ~";
            summaryColor = TEXT_DIM;
        }

        g2.setFont(new Font(UIConfig.FONT_CINZEL, Font.BOLD, 17));
        g2.setColor(summaryColor);
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(summary, 300 - fm.stringWidth(summary)/2, y + 22);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void g2d_fillRoundRect(Graphics2D g2, int x, int y, int w, int h, int arc) {
        g2.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
    }

    private void styleCloseButton(JButton btn) {
        btn.setFont(new Font(UIConfig.FONT_SEGOE_UI, Font.BOLD, 14));
        btn.setBackground(new Color(42, 50, 65));
        btn.setForeground(TEXT_MAIN);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(GOLD, 1),
                BorderFactory.createEmptyBorder(8, 28, 8, 28)));
        btn.setFocusPainted(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseEntered(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(62, 72, 95));
            }
            @Override public void mouseExited(java.awt.event.MouseEvent e) {
                btn.setBackground(new Color(42, 50, 65));
            }
        });
    }
}