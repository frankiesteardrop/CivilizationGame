package controller;

import com.google.gson.Gson;
import model.*;
import model.trade.BazaarTradeStrategy;
import model.trade.TradeStrategy;
import model.trade.TradingPostTradeStrategy;
import network.client.NetworkManager;
import network.messages.game.NpcTradeRequest;
import network.messages.game.TradeResponseRequest;
import network.messages.game.CancelTradeRequest;

public class TradeController implements TurnListener {

    private GameMap map;
    private NetworkManager networkManager;
    private final Gson gson = new Gson();

    public TradeController(GameMap map) {
        this.map = map;
        GameEventDispatcher.addListener(this);
    }

    public TradeController(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public TradeController(GameMap map, NetworkManager networkManager) {
        this.map = map;
        this.networkManager = networkManager;
        GameEventDispatcher.addListener(this);
    }

    public void setNetworkManager(NetworkManager networkManager) {
        this.networkManager = networkManager;
    }

    public void respondToTradeOffer(String tradeId, boolean accepted) {
        if (networkManager != null && networkManager.isConnected()) {
            networkManager.sendRequest(gson.toJson(new TradeResponseRequest(tradeId, accepted)));
        }
    }

    public void cancelTradeOffer(String tradeId) {
        if (networkManager != null && networkManager.isConnected()) {
            networkManager.sendRequest(gson.toJson(new CancelTradeRequest(tradeId)));
        }
    }


    public Inventory getPlayerInventory() {
        if (map == null) return null;
        return map.getTownHall().getInventory();
    }

    public boolean isCommercialAllied() {
        return false;
    }

    public int getBazaarTradeAmount(int level) {
        return (level == 1) ? 10 : (level == 2) ? 100 : 500;
    }

    public static class TradePreview {
        public final boolean isValid;
        public final String errorMessage;
        public final int receivedAmount;

        public TradePreview(boolean isValid, String errorMessage, int receivedAmount) {
            this.isValid = isValid;
            this.errorMessage = errorMessage;
            this.receivedAmount = receivedAmount;
        }
    }

    public TradePreview previewBazaarTrade(Bazaar bazaar, ResourceType give, ResourceType get) {
        if (map == null) return new TradePreview(false, "Map not loaded", 0);
        if (give == get) return new TradePreview(false, "Cannot trade a resource for itself!", 0);

        int currentLevel = bazaar.getLevel();
        int amountToGive = getBazaarTradeAmount(currentLevel);

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) {
            return new TradePreview(false, "Not enough " + give.name() + " to trade!", 0);
        }

        TradeStrategy strategy = new BazaarTradeStrategy(currentLevel);
        int received = strategy.calculateReceivedAmount(amountToGive, get, isCommercialAllied());

        if (received <= 0) {
            return new TradePreview(false, "Amount too small for an exchange!", 0);
        }

        int currentGet = inv.getResourceAmount(get);
        int capGet = inv.getCapacity(get);
        if (currentGet + received > capGet) {
            return new TradePreview(false, "Storage full! Need space for " + received + " " + get.name(), 0);
        }

        return new TradePreview(true, null, received);
    }

    public TradePreview previewTradingPostTrade(ResourceType give, int amountToGive, ResourceType get) {
        if (map == null) return new TradePreview(false, "Map not loaded", 0);
        if (give == get) return new TradePreview(false, "Cannot trade a resource for itself!", 0);

        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) {
            return new TradePreview(false, "Not enough " + give.name() + "!", 0);
        }

        TradeStrategy strategy = new TradingPostTradeStrategy();
        int received = strategy.calculateReceivedAmount(amountToGive, get, false);

        if (received <= 0) {
            return new TradePreview(false, "Amount too small for an 80% return!", 0);
        }

        int currentGet = inv.getResourceAmount(get);
        int capGet = inv.getCapacity(get);
        if (currentGet + received > capGet) {
            return new TradePreview(false, "Storage full! Need space for " + received + " " + get.name(), 0);
        }

        return new TradePreview(true, null, received);
    }

    public TradePreview previewTribeTrade(Tribe tribe, ResourceType give, int amount, ResourceType get) {
        if (map == null) return new TradePreview(false, "Map not loaded", 0);
        if (give == get)  return new TradePreview(false, "Cannot trade a resource for itself!", 0);
        if (amount <= 0)  return new TradePreview(false, "Amount must be greater than zero!", 0);

        Inventory inv = map.getTownHall().getInventory();
        if (inv == null || !inv.hasEnough(give, amount))
            return new TradePreview(false, "Not enough " + give.name() + "!", 0);

        double rate = (tribe.getType() == TribeType.COMMERCIAL) ? 0.80 : 0.75;
        int received = (int)(amount * rate);

        if (received <= 0)
            return new TradePreview(false, "Amount too small for any return!", 0);

        int currentGet = inv.getResourceAmount(get);
        int capGet     = inv.getCapacity(get);
        if (currentGet + received > capGet)
            return new TradePreview(false, "Storage full! Need space for " + received + " " + get.name(), 0);

        return new TradePreview(true, null, received);
    }

    public boolean tradeWithBazaar(Bazaar bazaar, ResourceType give, ResourceType get) {
        if (networkManager != null && networkManager.isConnected()) {
            networkManager.sendRequest(gson.toJson(new NpcTradeRequest(map.getHexOfBuilding(bazaar).getQ(), map.getHexOfBuilding(bazaar).getR(), "BAZAAR", give, 0, get)));
            return true;
        }
        if (bazaar.hasTraded()) return false;
        int currentLevel = bazaar.getLevel();
        int amountToGive = getBazaarTradeAmount(currentLevel);
        TradeStrategy strategy = new BazaarTradeStrategy(currentLevel);
        return executeTrade(give, amountToGive, get, strategy, () -> bazaar.setTraded(true));
    }

    public boolean tradeWithTradingPost(TradingPost post, Hex postHex, ResourceType give, int amount, ResourceType get) {
        if (networkManager != null && networkManager.isConnected()) {
            networkManager.sendRequest(gson.toJson(new NpcTradeRequest(postHex.getQ(), postHex.getR(), "TRADING_POST", give, amount, get)));
            return true;
        }
        if (post.hasTraded() || postHex == null || !postHex.isInsideBorder()) return false;
        TradeStrategy strategy = new TradingPostTradeStrategy();
        return executeTrade(give, amount, get, strategy, () -> post.setTraded(true));
    }

    private boolean executeTrade(ResourceType give, int amountToGive, ResourceType get, TradeStrategy strategy, Runnable onSuccess) {
        if (map == null) return false;
        Inventory inv = map.getTownHall().getInventory();
        if (!inv.hasEnough(give, amountToGive)) return false;
        int received = strategy.calculateReceivedAmount(amountToGive, get, false);
        if (received <= 0) return false;
        inv.consumeResource(give, amountToGive);
        inv.addResource(get, received);
        onSuccess.run();
        return true;
    }

    public boolean canUpgradeBazaar(Bazaar bazaar) {
        if (map == null || bazaar == null || !bazaar.canUpgrade()) return false;
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
        if (map == null) return;
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