package view;

import controller.TradeController;
import model.GameEventDispatcher;
import model.Hex;
import model.Inventory;
import model.ResourceType;
import model.TradingPost;
import model.trade.TradingPostTradeStrategy;

import javax.swing.*;
import java.awt.*;

public class TradingPostTradeDialog extends JDialog {

    public TradingPostTradeDialog(JFrame parent, TradingPost post, Hex hex, TradeController tradeController) {
        super(parent, "🏪 Trading Post Trade (80% Fixed Rate)", true);
        setSize(400, 360);
        setLocationRelativeTo(parent);
        getContentPane().setBackground(new Color(25, 28, 35));

        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(8, 10, 8, 10);

        JLabel info = new JLabel("Trading Post Market", SwingConstants.CENTER);
        info.setForeground(new Color(46, 204, 113));
        info.setFont(new Font("Segoe UI", Font.BOLD, 15));
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
        JLabel amountLbl = new JLabel("Amount:");
        amountLbl.setForeground(Color.LIGHT_GRAY);
        content.add(amountLbl, gbc);

        // ورودی سفارشی مقدار در مرکز تجارت
        JSpinner amountSpinner = new JSpinner(new SpinnerNumberModel(10, 1, 9999, 5));
        amountSpinner.getEditor().getComponent(0).setBackground(new Color(35, 39, 48));
        ((JSpinner.DefaultEditor) amountSpinner.getEditor()).getTextField().setForeground(Color.WHITE);
        gbc.gridx = 1;
        content.add(amountSpinner, gbc);

        gbc.gridx = 0; gbc.gridy = 3;
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
        gbc.gridx = 0; gbc.gridy = 4; gbc.gridwidth = 2;
        content.add(preview, gbc);

        JButton confirmBtn = new JButton("✅ Confirm Trade");
        confirmBtn.setBackground(new Color(52, 152, 219));
        confirmBtn.setForeground(Color.WHITE);
        confirmBtn.setFocusPainted(false);
        gbc.gridy = 5;
        content.add(confirmBtn, gbc);

        Runnable updatePreview = () -> {
            ResourceType give = types[giveBox.getSelectedIndex()];
            ResourceType get = types[getBox.getSelectedIndex()];
            int amount = (int) amountSpinner.getValue();

            if (give == get) {
                preview.setText("⚠️ Cannot trade a resource for itself!");
                preview.setForeground(new Color(231, 76, 60));
                confirmBtn.setEnabled(false);
                return;
            }

            Inventory inv = tradeController.getPlayerInventory();
            boolean hasEnoughGive = inv.hasEnough(give, amount);

            int recv = new TradingPostTradeStrategy().calculateReceivedAmount(amount, get, false);

            int currentGet = inv.getResourceAmount(get);
            int capGet = inv.getCapacity(get);
            boolean hasCapacity = (currentGet + recv <= capGet);

            if (!hasEnoughGive) {
                preview.setText("⚠️ Not enough " + give.name() + "!");
                preview.setForeground(new Color(231, 76, 60));
                confirmBtn.setEnabled(false);
            } else if (recv <= 0) {
                preview.setText("⚠️ Amount too small for an 80% return!");
                preview.setForeground(new Color(231, 76, 60));
                confirmBtn.setEnabled(false);
            } else if (!hasCapacity) {
                preview.setText("⚠️ Storage full! Need space for " + recv + " " + get.name());
                preview.setForeground(new Color(231, 76, 60));
                confirmBtn.setEnabled(false);
            } else {
                preview.setText("You will receive: " + recv + " " + get.name() + " ✅");
                preview.setForeground(new Color(46, 204, 113));
                confirmBtn.setEnabled(true);
            }
        };

        giveBox.addActionListener(e -> updatePreview.run());
        getBox.addActionListener(e -> updatePreview.run());
        amountSpinner.addChangeListener(e -> updatePreview.run());
        updatePreview.run();

        confirmBtn.addActionListener(e -> {
            ResourceType give = types[giveBox.getSelectedIndex()];
            ResourceType get = types[getBox.getSelectedIndex()];
            int amount = (int) amountSpinner.getValue();

            if (tradeController.tradeWithTradingPost(post, hex, give, amount, get)) {
                GameEventDispatcher.fireNotification("✅ Trading Post trade successful!");
                this.dispose();
            } else {
                GameEventDispatcher.fireNotification("❌ Trade failed. Check storage capacity and resources.");
            }
        });

        add(content);
    }
}