package controller;

import model.*;
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

    public Unit    selectUnitAt(Hex hex)               { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex)   { return unitController.canMove(unit, targetHex, gameMap); }
    public void    executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    public boolean isHostile(Hex hex) {
        if (hex == null) return false;
        boolean hasAnimal = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR() && u.getType() == UnitType.BEAR);
        boolean hasEnemyUnit = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR() && u.isEnemy());
        boolean hasTribeEnemy = (hex.getBuilding() instanceof TribeCamp camp) && camp.getTribe().getState().isHostile();
        return hasAnimal || hasEnemyUnit || hasTribeEnemy;
    }

    public boolean isCapturable(Unit unit, Hex hex) {
        if (unit == null || hex == null) return false;
        int dist = gameMap.getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR());
        return unit.getAttackRange() > 0 && !isHostile(hex) && !hex.isInsideBorder() && dist == 1;
    }

    // ─── MVC CLEANUP: تفویض ساخت‌وساز رابط کاربری به لایه View ───

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