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
    private final TradeController tradeController;
    private final TribeController tribeController;
    private final SaveLoadController saveLoadController; // ثبت سیستم Save

    public MainController(GameMap gameMap) {
        this.gameMap = gameMap;
        this.economyController = new EconomyController(this);
        this.tradeController = new TradeController(gameMap);
        this.tribeController = new TribeController(gameMap);
        this.turnController = new TurnController(this, gameMap);
        this.unitController = new UnitController();
        this.buildController = new BuildController(gameMap);
        this.upgradeController = new UpgradeController(gameMap);
        this.saveLoadController = new SaveLoadController(this); // مقداردهی
    }

    public GameMap getGameMap() { return gameMap; }
    public TurnController getTurnController() { return turnController; }
    public UnitController getUnitController() { return unitController; }
    public BuildController getBuildController() { return buildController; }
    public UpgradeController getUpgradeController() { return upgradeController; }
    public EconomyController getEconomyController() { return economyController; }
    public TradeController getTradeController() { return tradeController; }
    public TribeController getTribeController() { return tribeController; }
    public SaveLoadController getSaveLoadController() { return saveLoadController; }

    public Unit selectUnitAt(Hex hex) { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex) { return unitController.canMove(unit, targetHex); }
    public void executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    public List<MenuAction> getTownHallMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        TownHall th = gameMap.getTownHall();

        boolean qEmpty = th.isProductionQueueEmpty();
        String prefix = qEmpty ? "" : "⏳ [BUSY] ";
        boolean isPopCapped = gameMap.getAliveUnitsCount() >= gameMap.getUnitCap();
        String popPrefix = isPopCapped ? "👥 [CAP REACHED] " : prefix;

        int whWoodCost = th.getLevel() == 1 ? GameConfig.TH_UPGRADE_LVL2_WOOD : 0;
        int whStoneCost = th.getLevel() == 1 ? GameConfig.TH_UPGRADE_LVL2_STONE : GameConfig.TH_UPGRADE_LVL3_STONE;
        String whLabel = th.getLevel() >= 3
                ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TownHall Level %d", th.getLevel() + 1);

        actions.add(new MenuAction(whLabel, upgradeController.canAffordWarehouseUpgrade(), () -> upgradeController.handleWarehouseUpgrade()));

        actions.add(new MenuAction(th.isStoneMineUnlocked() ? "✅ ⛏️ Tech: Stone Mine" : String.format(prefix + "⛏️ Tech: Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"), () -> upgradeController.unlockTech("STONE_MINE")));
        actions.add(new MenuAction(th.isIronMineUnlocked() ? "✅ 🔩 Tech: Iron Mine" : String.format(prefix + "🔩 Tech: Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"), () -> upgradeController.unlockTech("IRON_MINE")));
        actions.add(new MenuAction(th.isProfessionalToolsUnlocked() ? "✅ 🔧 Tech: Steel Tools" : String.format(prefix + "🔧 Tech: Steel Tools (%dI)", GameConfig.TECH_STEEL_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"), () -> upgradeController.unlockTech("PROF_TOOLS")));
        actions.add(new MenuAction(th.isSeafaringUnlocked() ? "✅ ⛵ Tech: Seafaring" : String.format(prefix + "⛵ Tech: Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                upgradeController.canUnlockTech("SEAFARING"), () -> upgradeController.unlockTech("SEAFARING")));
        actions.add(new MenuAction(th.isDefensiveArchUnlocked() ? "✅ 🏰 Tech: Defensive Arch" : String.format(prefix + "🏰 Tech: Defensive Arch (%dS)", GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                upgradeController.canUnlockTech("DEFENSIVE_ARCH"), () -> upgradeController.unlockTech("DEFENSIVE_ARCH")));

        actions.add(new MenuAction(String.format(popPrefix + "👷 Train Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                upgradeController.canTrainUnit("WORKER"), () -> upgradeController.trainUnit("WORKER")));
        actions.add(new MenuAction(String.format(popPrefix + "🔨 Train Builder (%dF, %dW)", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                upgradeController.canTrainUnit("BUILDER"), () -> upgradeController.trainUnit("BUILDER")));
        actions.add(new MenuAction(String.format(popPrefix + "🧭 Train Explorer (%dF, %dW)", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                upgradeController.canTrainUnit("EXPLORER"), () -> upgradeController.trainUnit("EXPLORER")));
        actions.add(new MenuAction(String.format(popPrefix + "⚔️ Train Swordsman (20F, 10W)"),
                upgradeController.canTrainUnit("SWORDSMAN"), () -> upgradeController.trainUnit("SWORDSMAN")));
        actions.add(new MenuAction(String.format(popPrefix + "🏹 Train Archer (20F, 20W)"),
                upgradeController.canTrainUnit("ARCHER"), () -> upgradeController.trainUnit("ARCHER")));

        // دکمه‌های دستی Save در منوی TownHall اضافه شد
        actions.add(new MenuAction("💾 Save Game (Slot 1)", true, () -> saveLoadController.saveGame("slot1")));
        actions.add(new MenuAction("💾 Save Game (Slot 2)", true, () -> saveLoadController.saveGame("slot2")));
        actions.add(new MenuAction("💾 Save Game (Slot 3)", true, () -> saveLoadController.saveGame("slot3")));

        return actions;
    }

    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex hex) {
        List<MenuAction> actions = new ArrayList<>();
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
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
                actions.add(createBuildAction(builder, hex, BuildingType.DOCK, "⚓ Build Dock"));
                actions.add(createBuildAction(builder, hex, BuildingType.MONUMENT, "🏛️ Build Monument"));
                actions.add(createBuildAction(builder, hex, BuildingType.BAZAAR, "⚖️ Build Bazaar"));
            }
        } else if (selectedUnit.getType() == UnitType.WORKER) {
            Worker worker = (Worker) selectedUnit;
            if (worker.isStationed()) {
                actions.add(new MenuAction("🚪 Leave Facility", unitController.canEject(worker), () -> unitController.handleEject(worker)));
            } else {
                Building building = hex.getBuilding();
                if (building != null && !building.isDestroyed() && building.getType() != BuildingType.TOWN_HALL) {
                    boolean canStation = unitController.canStation(worker, hex);
                    actions.add(new MenuAction("⚙️ Station in " + building.getType().name(), canStation, () -> unitController.handleStation(worker, hex)));
                } else {
                    actions.add(new MenuAction("⛔ No workable facility", false, null));
                }
            }
        }
        return actions;
    }

    private MenuAction createBuildAction(Builder builder, Hex hex, BuildingType type, String label) {
        boolean canBuild = buildController.canBuild(type, hex, builder);
        return new MenuAction(label + " (-" + type.getApCost() + "AP)", canBuild, () -> {
            buildController.buildStructure(builder, type, hex);
            GameEventDispatcher.fireUnitStateChanged(builder);
        });
    }
}