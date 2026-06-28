package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class HUDPanel extends JPanel implements GameEventListener {
    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JPanel infoContainer; // کانتینر دربرگیرنده کارت‌های اطلاعات
    private final JButton endTurnBtn;
    private boolean confirmIdleMode = false;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(25, 28, 33)); // تم دارک اصلی

        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 4, 0, new Color(41, 128, 185)), // نوار آبی سلطنتی پایین
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        // کانتینر کارتی با چیدمان افقی و فاصله‌های استاندارد
        infoContainer = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        infoContainer.setOpaque(false); // شفاف برای نمایش بک‌گراند پنل اصلی
        add(infoContainer, BorderLayout.CENTER);

        endTurnBtn = new JButton("END TURN");
        endTurnBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        endTurnBtn.setBackground(new Color(192, 57, 43));
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFocusPainted(false);
        endTurnBtn.setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
        endTurnBtn.setCursor(new Cursor(Cursor.HAND_CURSOR));

        endTurnBtn.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) { endTurnBtn.setBackground(endTurnBtn.getBackground().brighter()); }
            @Override
            public void mouseExited(MouseEvent e) { updateButtonColor(); }
        });

        endTurnBtn.addActionListener(e -> handleEndTurn());
        add(endTurnBtn, BorderLayout.EAST);

        GameEventDispatcher.addListener(this);
        updateHUD();
    }

    private void updateButtonColor() {
        if (confirmIdleMode) endTurnBtn.setBackground(new Color(230, 126, 34));
        else endTurnBtn.setBackground(new Color(192, 57, 43));
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onUnitKilled(Unit unit) { SwingUtilities.invokeLater(this::updateHUD); }
    @Override public void onProductionCompleted(String itemName) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onTurnEnded(int newTurn) {
        SwingUtilities.invokeLater(() -> {
            confirmIdleMode = false;
            endTurnBtn.setText("END TURN");
            updateButtonColor();
            updateHUD();
            gamePanel.repaint();
        });
    }

    private void updateHUD() {
        // پاک کردن کانتینر برای رسم مجدد کارت‌های آپدیت‌شده
        infoContainer.removeAll();

        GameMap map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();
        int max = inv.getMaxCapacity();

        int netFood = EconomyManager.calculateNetProduction(map, ResourceType.FOOD);
        int netWood = EconomyManager.calculateNetProduction(map, ResourceType.WOOD);
        int netStone = EconomyManager.calculateNetProduction(map, ResourceType.STONE);
        int netIron = EconomyManager.calculateNetProduction(map, ResourceType.IRON);

        boolean isStarving = inv.getResourceAmount(ResourceType.FOOD) == 0 && netFood < 0;

        // کارت‌های منابع با رنگ‌های اختصاصی
        infoContainer.add(createResourceCard("🍔 Food", inv.getResourceAmount(ResourceType.FOOD), max, netFood, new Color(46, 204, 113)));
        infoContainer.add(createResourceCard("🪵 Wood", inv.getResourceAmount(ResourceType.WOOD), max, netWood, new Color(211, 84, 0)));
        infoContainer.add(createResourceCard("🪨 Stone", inv.getResourceAmount(ResourceType.STONE), max, netStone, new Color(149, 165, 166)));
        infoContainer.add(createResourceCard("⚙️ Iron", inv.getResourceAmount(ResourceType.IRON), max, netIron, new Color(243, 156, 18)));

        // کارت صف تولید (Queue)
        TownHall.ProductionTask currentTask = map.getTownHall().getProductionQueue().peek();
        String queueText;
        Color queueColor = new Color(241, 196, 15);
        if (currentTask != null) {
            queueText = currentTask.getName() + " (" + currentTask.getTurnsRemaining() + "T)";
            if (isStarving) {
                queueText += " <span style='color:#e74c3c;'>(FROZEN)</span>";
                queueColor = new Color(231, 76, 60);
            }
        } else {
            queueText = "<span style='color:#7f8c8d;'>Idle</span>";
            queueColor = new Color(127, 140, 141);
        }
        infoContainer.add(createCard("🏗️ Queue", queueText, queueColor, false));

        // کارت جمعیت و یونیت‌ها
        int e = 0, b = 0, w = 0, x = 0;
        for (Unit u : map.getUnits()) {
            if (u.isAlive()) {
                if (u instanceof Explorer) e++;
                else if (u instanceof Builder) b++;
                else if (u instanceof Worker) w++;
                else if (u instanceof BorderExpander) x++;
            }
        }
        String unitText = map.getAliveUnitsCount() + "/" + map.getUnitCap() + " <span style='font-size:10px; color:#bdc3c7;'>(E:" + e + " B:" + b + " W:" + w + " X:" + x + ")</span>";
        infoContainer.add(createCard("👥 Pop", unitText, new Color(52, 152, 219), false));

        // کارت شماره نوبت (Turn)
        infoContainer.add(createCard("⏳ Turn", String.valueOf(map.getCurrentTurn()), new Color(155, 89, 182), false));

        // اخطار قحطی (کارت چشمک‌زن/قرمز تند)
        if (isStarving) {
            infoContainer.add(createCard("⚠️ ALERT", "STARVATION", new Color(231, 76, 60), true));
        }

        // رفرش و رندر مجدد کانتینر
        infoContainer.revalidate();
        infoContainer.repaint();
    }

    // متد کمکی برای ساخت کارت‌های منابع
    private JPanel createResourceCard(String title, int amount, int max, int net, Color accentColor) {
        String netColor = net < 0 ? "#e74c3c" : "#2ecc71";
        String sign = net > 0 ? "+" : "";
        String valueText = amount + "<span style='color:#7f8c8d'>/" + max + "</span> "
                + "(<span style='color:" + netColor + "'>" + sign + net + "</span>)";
        return createCard(title, valueText, accentColor, false);
    }

    // متد کارخانه (Factory) برای تولید پنل‌های شیک و تفکیک‌شده
    private JPanel createCard(String title, String valueText, Color accentColor, boolean isAlert) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(isAlert ? new Color(192, 57, 43) : new Color(40, 44, 52)); // رنگ پس‌زمینه کارت

        // ایجاد حاشیه (Border) ترکیبی: یک خط رنگی در سمت چپ به عنوان شاخص بصری + پدینگ داخلی
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 4, 0, 0, accentColor),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));

        String titleStyle = isAlert ? "color:white;" : "color:#bdc3c7;";
        JLabel label = new JLabel("<html><body style='color:white; font-family:Segoe UI; font-size:13px; margin:0; padding:0;'>"
                + "<span style='" + titleStyle + " margin-right:5px;'>" + title + ":</span>" + valueText
                + "</body></html>");
        card.add(label, BorderLayout.CENTER);

        return card;
    }

    private void handleEndTurn() {
        if (!confirmIdleMode && mainController.getTurnController().hasIdleUnits()) {
            confirmIdleMode = true;
            endTurnBtn.setText("IDLE UNITS! CONFIRM");
            updateButtonColor();
        } else {
            confirmIdleMode = false;
            mainController.getTurnController().forceEndTurn();
        }
    }
}