package view;

import controller.TradeController;
import model.Bazaar;
import model.GameEventDispatcher;
import model.ResourceType;

import javax.swing.*;
import java.awt.*;

public class BazaarTradeDialog extends JDialog {

    public BazaarTradeDialog(JFrame parent, Bazaar bazaar, TradeController tradeController) {
        super(parent, "⚖️ Bazaar Trade (Level " + bazaar.getLevel() + ")", true);
        setSize(400, 350);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(new Color(25, 28, 35));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 10, 8, 10);

        int level = bazaar.getLevel();
        // ─── [MVC FIX]: Delegate value retrieval to Controller ───
        int amount = tradeController.getBazaarTradeAmount(level);

        JLabel info = new JLabel("Trade Amount: " + amount + " units", SwingConstants.CENTER);
        info.setForeground(new Color(241, 196, 15));
        info.setFont(new Font("Segoe UI", Font.BOLD, 14));
        gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;
        content.add(info, gbc);

        ResourceType[] types = {ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.IRON};
        String[] labels = {"🍔 Food", "🪵 Wood", "🪨 Stone", "⚙️ Iron"};

        gbc.gridy = 1; gbc.gridwidth = 1;
        JLabel giveLbl = new JLabel("Give:");
        giveLbl.setForeground(Color.LIGHT_GRAY);
        content.add(giveLbl, gbc);

        JComboBox<String> giveBox = new JComboBox<>(labels);
        giveBox.setBackground(new Color(35, 39, 48));
        giveBox.setForeground(Color.WHITE);
        gbc.gridx = 1;
        content.add(giveBox, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel getLbl = new JLabel("Receive:");
        getLbl.setForeground(Color.LIGHT_GRAY);
        content.add(getLbl, gbc);

        JComboBox<String> getBox = new JComboBox<>(labels);
        getBox.setBackground(new Color(35, 39, 48));
        getBox.setForeground(Color.WHITE);
        gbc.gridx = 1;
        content.add(getBox, gbc);

        JLabel preview = new JLabel("You receive: ~", SwingConstants.CENTER);
        preview.setFont(new Font("Segoe UI", Font.BOLD, 13));
        gbc.gridx = 0; gbc.gridy = 3; gbc.gridwidth = 2;
        content.add(preview, gbc);

        JButton confirmBtn = new JButton("✅ Confirm Trade");
        confirmBtn.setBackground(new Color(52, 152, 219));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        gbc.gridy = 4;
        content.add(confirmBtn, gbc);

        Runnable updatePreview = () -> {
            ResourceType give = types[giveBox.getSelectedIndex()];
            ResourceType get = types[getBox.getSelectedIndex()];

            TradeController.TradePreview previewResult = tradeController.previewBazaarTrade(bazaar, give, get);

            if (!previewResult.isValid) {
                preview.setText("⚠️ " + previewResult.errorMessage);
                preview.setForeground(new Color(231, 76, 60));
                confirmBtn.setEnabled(false);
            } else {
                preview.setText("You will receive: " + previewResult.receivedAmount + " " + get.name() + " ✅");
                preview.setForeground(new Color(46, 204, 113));
                confirmBtn.setEnabled(true);
            }
        };

        giveBox.addActionListener(e -> updatePreview.run());
        getBox.addActionListener(e -> updatePreview.run());
        updatePreview.run();

        confirmBtn.addActionListener(e -> {
            ResourceType give = types[giveBox.getSelectedIndex()];
            ResourceType get = types[getBox.getSelectedIndex()];
            if (tradeController.tradeWithBazaar(bazaar, give, get)) {
                GameEventDispatcher.fireNotification("✅ Trade successful!");
                this.dispose();
            } else {
                GameEventDispatcher.fireNotification("❌ Trade failed. Check storage capacity and resources.");
            }
        });

        add(content);
    }
}