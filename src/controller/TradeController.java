package controller;

import model.*;
import model.trade.BazaarTradeStrategy;
import model.trade.TradeStrategy;
import model.trade.TradingPostTradeStrategy;

public class TradeController implements TurnListener {

    private final GameMap map;

    public TradeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
    }

    public Inventory getPlayerInventory() {
        return map.getTownHall().getInventory();
    }

    public boolean isCommercialAllied() {
        return false;
    }

    public boolean tradeWithBazaar(Bazaar bazaar, ResourceType give, ResourceType get) {
        if (bazaar.hasTraded()) return false;

        int currentLevel = bazaar.getLevel();
        int amountToGive = (currentLevel == 1) ? 10 : (currentLevel == 2) ? 100 : 500;

        TradeStrategy strategy = new BazaarTradeStrategy(currentLevel);
        return executeTrade(give, amountToGive, get, strategy, () -> bazaar.setTraded(true));
    }

    public boolean tradeWithTradingPost(TradingPost post, Hex postHex, ResourceType give, int amount, ResourceType get) {
        if (post.hasTraded() || postHex == null || !postHex.isInsideBorder()) return false;

        TradeStrategy strategy = new TradingPostTradeStrategy();
        return executeTrade(give, amount, get, strategy, () -> post.setTraded(true));
    }

    private boolean executeTrade(ResourceType give, int amountToGive, ResourceType get, TradeStrategy strategy, Runnable onSuccess) {
        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) return false;

        int received = strategy.calculateReceivedAmount(amountToGive, get, false);
        if (received <= 0) return false;

        inv.consumeResource(give, amountToGive);
        inv.addResource(get, received);
        onSuccess.run();

        return true;
    }

    // ─── منطق ارتقای بازار منتقل شده از View به Controller (MVC Fix) ───

    public boolean canUpgradeBazaar(Bazaar bazaar) {
        if (bazaar == null || !bazaar.canUpgrade()) return false;
        int stoneCost = (bazaar.getLevel() == 1) ? 30 : 60;
        return map.getTownHall().getInventory().hasEnough(ResourceType.STONE, stoneCost);
    }

    public void upgradeBazaar(Bazaar bazaar) {
        if (!canUpgradeBazaar(bazaar)) return;
        int stoneCost = (bazaar.getLevel() == 1) ? 30 : 60;
        if (map.getTownHall().getInventory().consumeResource(ResourceType.STONE, stoneCost)) {
            bazaar.upgrade();
            GameEventDispatcher.fireNotification("⚖️ Bazaar upgraded to Level " + bazaar.getLevel() + "!");
        }
    }

    @Override
    public void onTurnEnded(int newTurn) {
        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed()) continue;
            if (b instanceof Bazaar)      ((Bazaar) b).setTraded(false);
            if (b instanceof TradingPost) ((TradingPost) b).setTraded(false);
            if (b instanceof TribeCamp)   ((TribeCamp) b).setTraded(false);
        }
    }

    @Override
    public void onStarvationChanged(boolean isStarving) {}
}