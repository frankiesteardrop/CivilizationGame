package controller;

import model.*;
import network.client.NetworkManager;
import java.util.List;

public class MainController {

    private final GameMap            gameMap;
    private final TurnController     turnController;
    private final UnitController     unitController;
    private final BuildController    buildController;
    private final UpgradeController  upgradeController;
    private final EconomyController  economyController;
    private final TradeController    tradeController;
    private final TribeController    tribeController;
    private final SaveLoadController saveLoadController;

    private NetworkManager networkManager = null;
    private String myPlayerId = null;
    private boolean myTurn = true;
    private boolean processingTurn = false;

    public MainController(GameMap gameMap) {
        this.gameMap            = gameMap;
        this.economyController  = new EconomyController(this);
        this.tradeController    = new TradeController(gameMap);
        this.tribeController    = new TribeController(gameMap);
        this.turnController     = new TurnController(this, gameMap);
        this.unitController     = new UnitController();
        this.buildController    = new BuildController(gameMap);
        this.upgradeController  = new UpgradeController(gameMap);
        this.saveLoadController = new SaveLoadController(this);

        boolean hasNoTribes = gameMap.getHexes().stream()
                .noneMatch(h -> h.getBuilding() instanceof TribeCamp);
        if (hasNoTribes) tribeController.spawnInitialTribes();
    }

    public void setNetworkManager(NetworkManager nm) { this.networkManager = nm; }
    public NetworkManager getNetworkManager()        { return networkManager; }

    public void setMyPlayerId(String id) { this.myPlayerId = id; }
    public String getMyPlayerId()        { return myPlayerId; }

    public void setMyTurn(boolean myTurn) { this.myTurn = myTurn; }
    public boolean isMyTurn() { return myTurn; }

    public GameMap            getGameMap()             { return gameMap; }
    public TurnController     getTurnController()      { return turnController; }
    public UnitController     getUnitController()      { return unitController; }
    public BuildController    getBuildController()     { return buildController; }
    public UpgradeController  getUpgradeController()   { return upgradeController; }
    public EconomyController  getEconomyController()   { return economyController; }
    public TradeController    getTradeController()     { return tradeController; }
    public TribeController    getTribeController()     { return tribeController; }
    public SaveLoadController getSaveLoadController()  { return saveLoadController; }

    public boolean isProcessingTurn()             { return processingTurn; }
    public void    setProcessingTurn(boolean val) { this.processingTurn = val; }

    public Unit    selectUnitAt(Hex hex)                 { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex)     { return unitController.canMove(unit, targetHex, gameMap); }
    public void    executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    public boolean isHostile(Hex hex) {
        if (hex == null) return false;

        boolean hasAnimal = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                        && u.getType() == UnitType.BEAR);

        if (networkManager != null && myPlayerId != null) {
            boolean hasOpponentUnit = gameMap.getUnits().stream()
                    .anyMatch(u -> u.isAlive()
                            && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                            && !myPlayerId.equals(u.getOwnerId())
                            && u.getType() != UnitType.BEAR);
            boolean hasOpponentBuilding = hex.getBuilding() != null
                    && !hex.getBuilding().isDestroyed()
                    && !myPlayerId.equals(hex.getBuilding().getOwnerId());
            return hasAnimal || hasOpponentUnit || hasOpponentBuilding;
        }

        boolean hasEnemyUnit  = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == hex.getQ() && u.getR() == hex.getR()
                        && u.isEnemy());
        boolean hasTribeEnemy = (hex.getBuilding() instanceof TribeCamp camp)
                && camp.getTribe().getState().isHostile();
        return hasAnimal || hasEnemyUnit || hasTribeEnemy;
    }

    // متد کلیدی: صرفا نمایش منوی حمله را برای کلاینت فعال می کند.
    // سرور باید تصمیم بگیرد که آیا حمله به لحاظ دیپلماتیک (وضعیت Enemy) معتبر است یا خیر.
    public boolean isAttackable(Hex hex) {
        if (hex == null) return false;
        if (isHostile(hex)) return true;
        if (hex.getBuilding() instanceof TribeCamp && !hex.getBuilding().isDestroyed()) return true;
        return false;
    }

    public boolean isCapturable(Unit unit, Hex hex) {
        if (unit == null || hex == null) return false;
        if (hex.getBuilding() instanceof TribeCamp) return false;
        int dist = gameMap.getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR());
        if (unit.getAttackRange() <= 0 || isHostile(hex) || hex.isInsideBorder() || dist != 1) return false;
        for (int i = 0; i < 6; i++) {
            Hex neighbor = gameMap.getNeighbor(hex, i);
            if (neighbor != null && neighbor.getBuilding() instanceof TribeCamp camp) {
                if (camp.getTribe().getState().getName().equals("Enemy")) return true;
            }
        }
        return false;
    }

    public List<MenuAction> getTownHallMenuActions() {
        return view.ContextMenuFactory.buildTownHallMenu(this);
    }

    public List<MenuAction> getStableMenuActions() {
        return view.ContextMenuFactory.buildStableMenu(this);
    }

    public List<MenuAction> getBazaarMenuActions(Bazaar bazaar, Runnable onTradeAction) {
        return view.ContextMenuFactory.buildBazaarMenu(this, bazaar, onTradeAction);
    }

    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex targetHex) {
        return view.ContextMenuFactory.buildUnitMenu(this, selectedUnit, targetHex);
    }
}