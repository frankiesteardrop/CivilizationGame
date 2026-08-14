package controller;

import model.*;

public class TradeController implements GameEventListener {

    private final GameMap map;

    public TradeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
    }

    // ─── F-26: بررسی اتحاد COMMERCIAL ────────────────────────────────────────

    /**
     * اگر قبیله تجاری (COMMERCIAL) متحد باشد، تمام تراکنش‌های Bazaar و
     * TradingPost 10% بیشتر برمی‌گرداند.
     * طبق spec: "Alliance با قبیله تجاری → +10% نرخ تجارت روی همه تراکنش‌ها"
     */
    private boolean isCommercialAllied() {
        return map.getHexes().stream()
                .anyMatch(h -> h.getBuilding() instanceof TribeCamp
                        && !h.getBuilding().isDestroyed()
                        && ((TribeCamp) h.getBuilding()).getTribe().isAllied()
                        && ((TribeCamp) h.getBuilding()).getTribe().getType()
                        == TribeType.COMMERCIAL);
    }

    /**
     * multiplier نرخ تجارت بر اساس اتحاد COMMERCIAL.
     * اتحاد COMMERCIAL → 1.10، بدون اتحاد → 1.00
     */
    private double getCommercialMultiplier() {
        return isCommercialAllied() ? 1.10 : 1.00;
    }

    // ─── Bazaar ──────────────────────────────────────────────────────────────

    /**
     * تجارت با Bazaar.
     * F-26: اگر COMMERCIAL متحد باشد، مقدار دریافتی ×1.10 می‌شود.
     *
     * نرخ‌های پایه (طبق spec):
     *   Level 1: 50% ← 10 واحد ورودی
     *   Level 2: 60% ← 100 واحد ورودی
     *   Level 3: 70% ← 500 واحد ورودی
     */
    public boolean tradeWithBazaar(Bazaar bazaar, int level,
                                   ResourceType give, ResourceType get) {
        if (bazaar.hasTraded()) return false;

        int    amountToGive = (level == 1) ? 10 : (level == 2) ? 100 : 500;
        double baseRate     = (level == 1) ? 0.5 : (level == 2) ? 0.6 : 0.7;
        double effectiveRate = baseRate * getCommercialMultiplier();

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) return false;

        inv.consumeResource(give, amountToGive);
        int received = (int) Math.floor(amountToGive * effectiveRate);
        inv.addResource(get, received);
        bazaar.setTraded(true);

        // notification اگر bonus فعال بود
        if (isCommercialAllied()) {
            GameEventDispatcher.fireNotification(
                    "💰 Commercial Alliance bonus: +10% trade rate applied!");
        }
        return true;
    }

    // ─── Trading Post ─────────────────────────────────────────────────────────

    /**
     * تجارت با Trading Post.
     * F-17 (از گام ۹): هکس باید در قلمرو بازیکن باشد.
     * F-26: اگر COMMERCIAL متحد باشد، نرخ ×1.10 می‌شود.
     *
     * نرخ پایه Trading Post: 80%
     */
    public boolean tradeWithTradingPost(TradingPost post, Hex postHex,
                                        ResourceType give, int amount,
                                        ResourceType get) {
        if (post.hasTraded()) return false;
        if (postHex == null || !postHex.isInsideBorder()) return false;

        double effectiveRate = 0.8 * getCommercialMultiplier();

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amount)) return false;

        inv.consumeResource(give, amount);
        int received = (int) Math.floor(amount * effectiveRate);
        inv.addResource(get, received);
        post.setTraded(true);

        if (isCommercialAllied()) {
            GameEventDispatcher.fireNotification(
                    "💰 Commercial Alliance bonus: +10% trade rate applied!");
        }
        return true;
    }

    // ─── Turn reset ───────────────────────────────────────────────────────────

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

    @Override public void onResourceChanged(ResourceType type, int newAmount) {}
    @Override public void onUnitMoved(Unit unit, int oldQ, int oldR, int newQ, int newR) {}
    @Override public void onUnitKilled(Unit unit) {}
    @Override public void onProductionCompleted(String itemName) {}
    @Override public void onStarvationChanged(boolean isStarving) {}
    @Override public void onUnitStateChanged(Unit unit) {}
    @Override public void onBuildingConstructed(Hex hex) {}
    @Override public void onBuildingDestroyed(Hex hex) {}
    @Override public void onBorderExpanded(int centerQ, int centerR) {}
    @Override public void onDisasterTriggered(String type, Hex center,
                                              java.util.List<Hex> affected) {}
    @Override public void onCombatTriggered(java.util.List<Integer> atk,
                                            java.util.List<Integer> def,
                                            int a, int d) {}
    @Override public void onNotification(String message) {}
}