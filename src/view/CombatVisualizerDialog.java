package view;

import javax.swing.*;
import java.awt.*;
import java.util.List;
import java.util.Collections;

public class CombatVisualizerDialog extends JDialog {
    private int tickCount = 0;
    private final Timer rollTimer;

    public CombatVisualizerDialog(JFrame parent, List<Integer> atkDice, List<Integer> defDice, int atkDmg, int defDmg) {
        super(parent, "⚔️ Combat Resolution", true);
        setSize(500, 350);
        setLocationRelativeTo(parent);
        setUndecorated(true);
        setLayout(new BorderLayout());

        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(new Color(25, 28, 33));
        mainPanel.setBorder(BorderFactory.createLineBorder(new Color(231, 76, 60), 3));

        JLabel title = new JLabel("Rolling Dice...", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 22));
        title.setForeground(Color.WHITE);
        title.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        mainPanel.add(title, BorderLayout.NORTH);

        JPanel dicePanel = new JPanel(new GridLayout(2, 1, 10, 10));
        dicePanel.setOpaque(false);

        JLabel atkLabel = new JLabel("", SwingConstants.CENTER);
        atkLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        atkLabel.setForeground(new Color(52, 152, 219));

        JLabel defLabel = new JLabel("", SwingConstants.CENTER);
        defLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        defLabel.setForeground(new Color(231, 76, 60));

        dicePanel.add(atkLabel);
        dicePanel.add(defLabel);
        mainPanel.add(dicePanel, BorderLayout.CENTER);

        JLabel resultLabel = new JLabel("", SwingConstants.CENTER);
        resultLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        resultLabel.setForeground(Color.YELLOW);
        resultLabel.setBorder(BorderFactory.createEmptyBorder(15, 0, 15, 0));
        mainPanel.add(resultLabel, BorderLayout.SOUTH);

        add(mainPanel);

        // انیمیشن رول شدن تاس‌ها
        rollTimer = new Timer(50, e -> {
            tickCount++;
            if (tickCount < 20) {
                atkLabel.setText("Attacker: " + randomRolls(atkDice.size()));
                defLabel.setText("Defender: " + randomRolls(defDice.size()));
            } else {
                rollTimer.stop();
                title.setText("⚔️ Combat Result");

                atkLabel.setText("Attacker: " + formatFinalDice(atkDice, defDice, true));
                defLabel.setText("Defender: " + formatFinalDice(atkDice, defDice, false));

                resultLabel.setText(String.format("Losses -> Attacker: %d Unit(s) | Defender: %d Hit(s)", atkDmg, defDmg));

                Timer closeTimer = new Timer(3500, ev -> dispose());
                closeTimer.setRepeats(false);
                closeTimer.start();
            }
        });
        rollTimer.start();
    }

    private String randomRolls(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i=0; i<count; i++) sb.append("[").append((int)(Math.random()*6)+1).append("] ");
        return sb.toString();
    }

    private String formatFinalDice(List<Integer> atk, List<Integer> def, boolean isAttacker) {
        StringBuilder sb = new StringBuilder("<html>");
        List<Integer> primary = isAttacker ? atk : def;
        List<Integer> secondary = isAttacker ? def : atk;

        for (int i = 0; i < primary.size(); i++) {
            int pVal = primary.get(i);
            String color = "white";
            if (i < secondary.size()) {
                int sVal = secondary.get(i);
                if (isAttacker) {
                    color = (pVal > sVal) ? "#2ecc71" : "#e74c3c"; // مهاجم باید بزرگتر باشد
                } else {
                    color = (pVal >= sVal) ? "#2ecc71" : "#e74c3c"; // مدافع با مساوی هم می‌برد
                }
            }
            sb.append("<span style='color:").append(color).append(";'>[").append(pVal).append("]</span> ");
        }
        return sb.append("</html>").toString();
    }
}