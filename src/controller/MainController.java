package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;

public class MainController {
    private final GameMap gameMap;
    private final TurnController turnController;
    private final UnitController unitController;
    private final BuildController buildController;
    private final UpgradeController upgradeController;
    private final EconomyController economyController;

    // کپسوله‌سازی برای تنظیمات صوتی به صورت سراسری
    private static final AudioController audioController = new AudioController();

    public MainController(GameMap gameMap) {
        this.gameMap = gameMap;
        this.economyController = new EconomyController(this);
        this.turnController = new TurnController(this, gameMap);
        this.unitController = new UnitController();
        this.buildController = new BuildController(gameMap);
        this.upgradeController = new UpgradeController(gameMap);
    }

    public GameMap getGameMap() { return gameMap; }
    public TurnController getTurnController() { return turnController; }
    public UnitController getUnitController() { return unitController; }
    public BuildController getBuildController() { return buildController; }
    public UpgradeController getUpgradeController() { return upgradeController; }
    public EconomyController getEconomyController() { return economyController; }

    public Unit selectUnitAt(Hex hex) { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex) { return unitController.canMove(unit, targetHex); }
    public void executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    // === بخش حیاتی MVC: ساختاردهی منوها در کنترلر ===

    public List<MenuAction> getTownHallMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        TownHall th = gameMap.getTownHall();

        int whWoodCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_WOOD : GameConfig.WAREHOUSE_LVL2_WOOD;
        int whStoneCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_STONE : GameConfig.WAREHOUSE_LVL2_STONE;
        String whLabel = th.getWarehouseUpgradeLevel() >= 2
                ? "✅ Warehouse MAXED"
                : String.format("📦 Upgrade Warehouse (%dW, %dS) — Level %d", whWoodCost, whStoneCost, th.getWarehouseUpgradeLevel() + 1);

        actions.add(new MenuAction(whLabel, upgradeController.canAffordWarehouseUpgrade(), () -> upgradeController.handleWarehouseUpgrade()));

        actions.add(new MenuAction(th.isStoneMineUnlocked() ? "✅ ⛏️ Tech: Stone Mine" : String.format("⛏️ Tech: Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"), () -> upgradeController.unlockTech("STONE_MINE")));
        actions.add(new MenuAction(th.isIronMineUnlocked() ? "✅ 🔩 Tech: Iron Mine" : String.format("🔩 Tech: Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"), () -> upgradeController.unlockTech("IRON_MINE")));
        actions.add(new MenuAction(th.isProfessionalToolsUnlocked() ? "✅ 🔧 Tech: Prof. Tools" : String.format("🔧 Tech: Prof. Tools (%dW, %dS, %dI)", GameConfig.TECH_PROF_TOOLS_WOOD, GameConfig.TECH_PROF_TOOLS_STONE, GameConfig.TECH_PROF_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"), () -> upgradeController.unlockTech("PROF_TOOLS")));
        actions.add(new MenuAction(th.isSettlementUnlocked() ? "✅ 🏘️ Tech: Settlement" : String.format("🏘️ Tech: Settlement (%dW, %dS, %dI)", GameConfig.TECH_SETTLEMENT_WOOD, GameConfig.TECH_SETTLEMENT_STONE, GameConfig.TECH_SETTLEMENT_IRON),
                upgradeController.canUnlockTech("SETTLEMENT"), () -> upgradeController.unlockTech("SETTLEMENT")));

        actions.add(new MenuAction(String.format("👷 Train Worker (%dF) — %d Turn", GameConfig.WORKER_FOOD_COST, GameConfig.WORKER_TURN_COST),
                upgradeController.canTrainUnit("WORKER"), () -> upgradeController.trainUnit("WORKER")));
        actions.add(new MenuAction(String.format("🔨 Train Builder (%dF, %dW) — %d Turns", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST, GameConfig.BUILDER_TURN_COST),
                upgradeController.canTrainUnit("BUILDER"), () -> upgradeController.trainUnit("BUILDER")));
        actions.add(new MenuAction(String.format("🧭 Train Explorer (%dF, %dW) — %d Turns", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST, GameConfig.EXPLORER_TURN_COST),
                upgradeController.canTrainUnit("EXPLORER"), () -> upgradeController.trainUnit("EXPLORER")));
        actions.add(new MenuAction(String.format("🗺️ Train Border Expander (%dF, %dW, %dS) — %d Turns", GameConfig.BORDER_EXPANDER_FOOD_COST, GameConfig.BORDER_EXPANDER_WOOD_COST, GameConfig.BORDER_EXPANDER_STONE_COST, GameConfig.BORDER_EXPANDER_TURN_COST),
                upgradeController.canTrainUnit("BORDER_EXPANDER"), () -> upgradeController.trainUnit("BORDER_EXPANDER")));

        return actions;
    }

    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex hex) {
        List<MenuAction> actions = new ArrayList<>();
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;
            if (hex.getBuilding() != null) {
                actions.add(new MenuAction("⛔ Hex already has a building", false, null));
            } else if (!hex.isInsideBorder()) {
                actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
            } else {
                actions.add(createBuildAction(builder, hex, BuildingType.LUMBER_MILL, "🌲 Build Lumber Mill"));
                actions.add(createBuildAction(builder, hex, BuildingType.FARM, "🌾 Build Farm"));
                actions.add(createBuildAction(builder, hex, BuildingType.STABLE, "🐄 Build Stable"));
                actions.add(createBuildAction(builder, hex, BuildingType.STONE_MINE, "⛏️ Build Stone Mine"));
                actions.add(createBuildAction(builder, hex, BuildingType.IRON_MINE, "🔩 Build Iron Mine"));
                actions.add(createBuildAction(builder, hex, BuildingType.SETTLEMENT, "🏘️ Build Settlement"));
            }
        } else if (selectedUnit.getType() == UnitType.WORKER) {
            Worker worker = (Worker) selectedUnit;
            if (worker.isStationed()) {
                actions.add(new MenuAction("🚪 Leave Facility (free — AP not restored)", unitController.canEject(worker), () -> unitController.handleEject(worker)));
            } else {
                Building building = hex.getBuilding();
                if (building != null && !building.isDestroyed() && building.getType() != BuildingType.TOWN_HALL) {
                    String label = "⚙️ Station in " + building.getType().name() + " (-" + Worker.getStationApCost() + " AP) [" + building.getStationedWorkers() + "/" + building.getMaxWorkers() + "]";
                    actions.add(new MenuAction(label, unitController.canStation(worker, hex), () -> unitController.handleStation(worker, hex)));
                } else {
                    actions.add(new MenuAction("⛔ No workable facility on this hex", false, null));
                }
            }
        } else if (selectedUnit.getType() == UnitType.BORDER_EXPANDER) {
            BorderExpander expander = (BorderExpander) selectedUnit;
            actions.add(new MenuAction("🗺️ Expand Border here (-" + BorderExpander.getExpandApCost() + " AP, unit consumed)",
                    expander.canExpand(gameMap), () -> unitController.handleExpandBorder(expander, gameMap)));
        }
        return actions;
    }

    private MenuAction createBuildAction(Builder builder, Hex hex, BuildingType type, String label) {
        String cost = "";
        if (type.getWoodCost() > 0) cost += type.getWoodCost() + "W ";
        if (type.getStoneCost() > 0) cost += type.getStoneCost() + "S ";
        if (type.getIronCost() > 0) cost += type.getIronCost() + "I";
        String fullLabel = label + " (-" + type.getApCost() + "AP" + (cost.isEmpty() ? "" : " | " + cost.trim()) + ")";
        return new MenuAction(fullLabel, buildController.canBuild(type, hex, builder), () -> buildController.buildStructure(builder, type, hex));
    }

    // متدهای استاتیک صدا جهت جلوگیری از تداخل با دیالوگ تنظیمات حفظ شدند (پاس داده می‌شوند به سیستم صوتی شی‌گرا)
    public static void setMusicVolume(int volumePercent) { audioController.setVolume(volumePercent); }
    public static int getMusicVolume() { return audioController.getCurrentVolume(); }
    public static void playMusic(String resourcePath) { audioController.playMusic(resourcePath); }
    public static void stopMusic() { audioController.stopMusic(); }
}