package controller;

import model.GameMap;

/**
 * الگوی طراحی Facade: این کلاس دسترسی متمرکز به تمامی کنترلرهای تخصصی را برای لایه View فراهم می‌کند.
 */
public class MainController {
    private final GameMap gameMap;
    private final TurnController turnController;
    private final UnitController unitController;
    private final BuildController buildController;
    private final UpgradeController upgradeController;

    public MainController(GameMap gameMap) {
        this.gameMap = gameMap;
        this.turnController = new TurnController(gameMap);
        this.unitController = new UnitController();
        this.buildController = new BuildController(gameMap);
        this.upgradeController = new UpgradeController(gameMap);
    }

    public GameMap getGameMap() { return gameMap; }
    public TurnController getTurnController() { return turnController; }
    public UnitController getUnitController() { return unitController; }
    public BuildController getBuildController() { return buildController; }
    public UpgradeController getUpgradeController() { return upgradeController; }
}