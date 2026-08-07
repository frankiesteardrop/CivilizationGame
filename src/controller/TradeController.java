package controller;

import model.*;

public class TradeController implements GameEventListener {
    private final GameMap map;

    public TradeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
    }

    public boolean tradeWithBazaar(Bazaar bazaar, int level, ResourceType give, ResourceType get) {
        if (bazaar.hasTraded()) return false;
        int amountToGive = (level == 1) ? 10 : (level == 2) ? 100 : 500;
        double rate = (level == 1) ? 0.5 : (level == 2) ? 0.6 : 0.7;

        Inventory inv = map.getTownHall().getInventory();
        if (inv.hasEnough(give, amountToGive)) {
            inv.consumeResource(give, amountToGive);
            inv.addResource(get, (int) Math.floor(amountToGive * rate));
            bazaar.setTraded(true);
            return true;
        }
        return false;
    }

    public boolean tradeWithTradingPost(TradingPost post, ResourceType give, int amount, ResourceType get) {
        if (post.hasTraded()) return false;
        Inventory inv = map.getTownHall().getInventory();
        if (inv.hasEnough(give, amount)) {
            inv.consumeResource(give, amount);
            inv.addResource(get, (int) Math.floor(amount * 0.8));
            post.setTraded(true);
            return true;
        }
        return false;
    }

    @Override public void onTurnEnded(int newTurn) {
        for (Hex hex : map.getHexes()) {
            if (hex.getBuilding() instanceof Bazaar) ((Bazaar) hex.getBuilding()).setTraded(false);
            if (hex.getBuilding() instanceof TradingPost) ((TradingPost) hex.getBuilding()).setTraded(false);
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
    @Override public void onCombatTriggered(java.util.List<Integer> atk, java.util.List<Integer> def, int aDmg, int dDmg) {}
    @Override public void onNotification(String message) {}
}