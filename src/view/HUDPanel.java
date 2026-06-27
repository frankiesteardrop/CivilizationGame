package view;

import controller.MainController;
import model.*;
import javax.swing.*;
import java.awt.*;

public class HUDPanel extends JPanel implements GameEventListener {
    private final MainController mainController;
    private final GamePanel gamePanel;
    private final JLabel infoLabel;

    public HUDPanel(MainController mainController, GamePanel gamePanel) {
        this.mainController = mainController;
        this.gamePanel = gamePanel;

        setLayout(new BorderLayout());
        setBackground(new Color(30, 32, 36)); // رنگ تیره و مدرن‌تر

        // ایجاد یک حاشیه ترکیبی: یک خط جداکننده در پایین و پدینگ داخلی برای زیبایی
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, new Color(70, 130, 180)), // خط استیل بلو در پایین
                BorderFactory.createEmptyBorder(8, 15, 8, 15)
        ));

        infoLabel = new JLabel();
        infoLabel.setForeground(Color.WHITE);
        infoLabel.setFont(new Font("SansSerif", Font.PLAIN, 15)); // فونت خواناتر برای اعداد
        add(infoLabel, BorderLayout.CENTER);

        JButton endTurnBtn = new JButton("End Turn");
        endTurnBtn.setFont(new Font("SansSerif", Font.BOLD, 14));
        endTurnBtn.setBackground(new Color(178, 34, 34)); // رنگ قرمز آجری شیک‌تر برای دکمه
        endTurnBtn.setForeground(Color.WHITE);
        endTurnBtn.setFocusPainted(false);
        endTurnBtn.setCursor(new Cursor(Cursor.HAND_CURSOR)); // تغییر شکل موس هنگام رفتن روی دکمه
        endTurnBtn.addActionListener(e -> handleEndTurn());
        add(endTurnBtn, BorderLayout.EAST);

        GameEventDispatcher.addListener(this);
        updateHUD();
    }

    @Override
    public void onResourceChanged(ResourceType type, int newAmount) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onUnitKilled(Unit unit) { SwingUtilities.invokeLater(this::updateHUD); }

    @Override
    public void onProductionCompleted(String itemName) { SwingUtilities.invokeLater(this::updateHUD); }

    // پیاده‌سازی متد جدید برای واکنش نشان دادن به رویداد پایان نوبت
    @Override
    public void onTurnEnded(int newTurn) {
        SwingUtilities.invokeLater(() -> {
            updateHUD();
            gamePanel.repaint(); // رفرش شدن نقشه بازی با شروع نوبت جدید
        });
    }

    private void updateHUD() {
        GameMap map = mainController.getGameMap();
        Inventory inv = map.getTownHall().getInventory();
        int max = inv.getMaxCapacity();

        String foodHtml = formatResource("Food", inv.getResourceAmount(ResourceType.FOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.FOOD));
        String woodHtml = formatResource("Wood", inv.getResourceAmount(ResourceType.WOOD), max, EconomyManager.calculateNetProduction(map, ResourceType.WOOD));
        String stoneHtml = formatResource("Stone", inv.getResourceAmount(ResourceType.STONE), max, EconomyManager.calculateNetProduction(map, ResourceType.STONE));
        String ironHtml = formatResource("Iron", inv.getResourceAmount(ResourceType.IRON), max, EconomyManager.calculateNetProduction(map, ResourceType.IRON));

        String unitHtml = " | &nbsp;<b>Units:</b> " + map.getAliveUnitsCount() + "/" + map.getUnitCap();
        String turnHtml = " | &nbsp;<font color='#00FFFF'><b>Turn: " + map.getCurrentTurn() + "</b></font>";

        infoLabel.setText("<html>" + foodHtml + woodHtml + stoneHtml + ironHtml + unitHtml + turnHtml + "</html>");
    }

    private String formatResource(String name, int amount, int max, int net) {
        String netColor = net < 0 ? "#FF6347" : "#32CD32"; // رنگ‌های ملایم‌تر برای مثبت و منفی
        String sign = net > 0 ? "+" : "";
        return String.format("<b>%s:</b> %d/%d (<font color='%s'>%s%d</font>) &nbsp;&nbsp;&nbsp;", name, amount, max, netColor, sign, net);
    }

    private void handleEndTurn() {
        // آپدیت دستی حذف شد. حالا فقط به کنترلر می‌گوییم نوبت را تمام کن.
        // اگر کنترلر موفق شود، خودش سیگنال onTurnEnded را شلیک می‌کند و رابط کاربری آپدیت می‌شود.
        mainController.getTurnController().tryEndTurn(this);
    }
}