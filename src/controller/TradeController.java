package controller;

import model.*;

public class TradeController implements GameEventListener {

    private final GameMap map;

    public TradeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
    }

    // ─── Bazaar ──────────────────────────────────────────────────────────────

    public boolean tradeWithBazaar(Bazaar bazaar, int level, ResourceType give, ResourceType get) {
        if (bazaar.hasTraded()) return false;

        int    amountToGive = (level == 1) ? 10 : (level == 2) ? 100 : 500;
        double rate         = (level == 1) ? 0.5 : (level == 2) ? 0.6 : 0.7;

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) return false;

        inv.consumeResource(give, amountToGive);
        inv.addResource(get, (int) Math.floor(amountToGive * rate));
        bazaar.setTraded(true);
        return true;
    }

    // ─── Trading Post ─────────────────────────────────────────────────────────

    /**
     * تجارت با Trading Post:
     * - هکس باید در قلمرو بازیکن باشد (F-17)
     * - نرخ: 80%، floor
     * - ۱ تراکنش/ترن
     */
    public boolean tradeWithTradingPost(TradingPost post, Hex postHex,
                                        ResourceType give, int amount, ResourceType get) {
        if (post.hasTraded()) return false;

        // F-17: Trading Post فقط اگر هکس در قلمرو بازیکن باشد
        if (postHex == null || !postHex.isInsideBorder()) return false;

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amount)) return false;

        inv.consumeResource(give, amount);
        inv.addResource(get, (int) Math.floor(amount * 0.8));
        post.setTraded(true);
        return true;
    }

    // ─── Turn reset ───────────────────────────────────────────────────────────

    @Override
    public void onTurnEnded(int newTurn) {
        for (Hex hex : map.getHexes()) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed()) continue;

            // ریست Bazaar
            if (b instanceof Bazaar) ((Bazaar) b).setTraded(false);

            // ریست Trading Post
            if (b instanceof TradingPost) ((TradingPost) b).setTraded(false);

            // ریست trade flag قبایل (F-07)
            if (b instanceof TribeCamp) ((TribeCamp) b).setTraded(false);
        }
    }

    @Override public void onResourceChanged(ResourceType type, int newAmount) {}
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {}
    @Override public void onUnitKilled(Unit unit) {}
    @Override public void onProductionCompleted(String itemName) {}
    @Override public void onStarvationChanged(boolean isStarving) {}
    @Override public void onUnitStateChanged(Unit unit) {}
    @Override public void onBuildingConstructed(Hex hex) {}
    @Override public void onBuildingDestroyed(Hex hex) {}
    @Override public void onBorderExpanded(int centerQ, int centerR) {}
    @Override public void onDisasterTriggered(String type, Hex center, java.util.List<Hex> affected) {}
    @Override public void onCombatTriggered(java.util.List<Integer> atk, java.util.List<Integer> def, int a, int d) {}
    @Override public void onNotification(String message) {}
}