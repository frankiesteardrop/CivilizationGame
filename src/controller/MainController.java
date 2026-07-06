package controller;

import model.*;

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

    public Unit selectUnitAt(Hex hex) {
        return unitController.selectUnitAt(hex, gameMap);
    }

    public boolean canMove(Unit unit, Hex targetHex) {
        return unitController.canMove(unit, targetHex);
    }

    public void executeMove(Unit unit, Hex targetHex) {
        unitController.executeMove(unit, targetHex, gameMap);
    }

    public boolean canStation(Worker worker, Hex hex) {
        return unitController.canStation(worker, hex);
    }

    public boolean handleStation(Worker worker, Hex hex) {
        return unitController.handleStation(worker, hex);
    }

    public boolean canEject(Worker worker) {
        return unitController.canEject(worker);
    }

    public void handleEject(Worker worker) {
        unitController.handleEject(worker);
    }

    public boolean handleExpandBorder(BorderExpander expander) {
        return unitController.handleExpandBorder(expander, gameMap);
    }


    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        return buildController.canBuild(type, hex, builder);
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        buildController.buildStructure(builder, type, hex);
    }


    public boolean canAffordWarehouseUpgrade() {
        return upgradeController.canAffordWarehouseUpgrade();
    }

    public void handleWarehouseUpgrade() {
        upgradeController.handleWarehouseUpgrade();
    }

    public boolean canUnlockTech(String techType) {
        return upgradeController.canUnlockTech(techType);
    }

    public void unlockTech(String techType) {
        upgradeController.unlockTech(techType);
    }

    public boolean canTrainUnit(String unitType) {
        return upgradeController.canTrainUnit(unitType);
    }

    public void trainUnit(String unitType) {
        upgradeController.trainUnit(unitType);
    }

    public static void setMusicVolume(int volumePercent) {
        AudioManager.setVolume(volumePercent);
    }

    public static int getMusicVolume() {
        return AudioManager.getCurrentVolume();
    }

    public static void playMusic(String resourcePath) {
        AudioManager.playMusic(resourcePath);
    }

    public static void stopMusic() {
        AudioManager.stopMusic();
    }
}