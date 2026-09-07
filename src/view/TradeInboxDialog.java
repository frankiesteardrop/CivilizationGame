package view;

import controller.TradeController;
import model.TradeOffer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;

public class TradeInboxDialog extends JDialog {

    private final TradeController tradeController;

    public TradeInboxDialog(JFrame parent, TradeController tradeController, List<TradeOffer> pendingOffers) {
        super(parent, "Trade Inbox", true);
        this.tradeController = tradeController;

        setSize(450, 500);
        setLocationRelativeTo(parent);
        setUndecorated(true);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(new Color(25, 28, 35));
        root.setBorder(BorderFactory.createLineBorder(new Color(230, 126, 34), 2));

        // Header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(35, 39, 48));
        header.setBorder(new EmptyBorder(15, 20, 15, 20));
        JLabel title = new JLabel("📥 TRADE INBOX", SwingConstants.CENTER);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.CENTER);

        JButton closeBtn = new JButton("✕");
        closeBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        closeBtn.setBackground(new Color(192, 57, 43));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setFocusPainted(false);
        closeBtn.setBorder(new EmptyBorder(4, 10, 4, 10));
        closeBtn.addActionListener(e -> dispose());
        header.add(closeBtn, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // Content
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBackground(new Color(25, 28, 35));

        if (pendingOffers == null || pendingOffers.isEmpty()) {
            JLabel emptyLabel = new JLabel("No pending trade offers.", SwingConstants.CENTER);
            emptyLabel.setFont(new Font("Segoe UI", Font.ITALIC, 14));
            emptyLabel.setForeground(Color.GRAY);
            emptyLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            listPanel.add(Box.createRigidArea(new Dimension(0, 50)));
            listPanel.add(emptyLabel);
        } else {
            for (TradeOffer offer : pendingOffers) {
                listPanel.add(buildOfferCard(offer));
                listPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            }
        }

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.setBorder(new EmptyBorder(10, 10, 10, 10));
        scroll.getViewport().setBackground(new Color(25, 28, 35));
        root.add(scroll, BorderLayout.CENTER);

        setContentPane(root);
    }

    private JPanel buildOfferCard(TradeOffer offer) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(new Color(40, 44, 52));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(52, 152, 219), 1),
                new EmptyBorder(10, 15, 10, 15)));

        JLabel senderLabel = new JLabel("From: " + offer.getOffererName());
        senderLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        senderLabel.setForeground(new Color(241, 196, 15));

        JLabel detailsLabel = new JLabel(String.format("<html><body>Receives: <b style='color:#2ecc71;'>%d %s</b><br/>Gives: <b style='color:#e74c3c;'>%d %s</b></body></html>",
                offer.getOfferAmount(), offer.getOfferType().name(),
                offer.getRequestAmount(), offer.getRequestType().name()));
        detailsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        detailsLabel.setForeground(Color.WHITE);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1));
        infoPanel.setOpaque(false);
        infoPanel.add(senderLabel);
        infoPanel.add(detailsLabel);
        card.add(infoPanel, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new GridLayout(2, 1, 0, 5));
        btnPanel.setOpaque(false);

        JButton acceptBtn = new JButton("✅ Accept");
        acceptBtn.setBackground(new Color(46, 204, 113));
        acceptBtn.setForeground(Color.WHITE);
        acceptBtn.setFocusPainted(false);
        acceptBtn.addActionListener(e -> {
            tradeController.respondToTradeOffer(offer.getId(), true);
            dispose();
        });

        JButton rejectBtn = new JButton("❌ Reject");
        rejectBtn.setBackground(new Color(192, 57, 43));
        rejectBtn.setForeground(Color.WHITE);
        rejectBtn.setFocusPainted(false);
        rejectBtn.addActionListener(e -> {
            tradeController.respondToTradeOffer(offer.getId(), false);
            dispose();
        });

        btnPanel.add(acceptBtn);
        btnPanel.add(rejectBtn);
        card.add(btnPanel, BorderLayout.EAST);

        return card;
    }
}