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
    private final SaveLoadController saveLoadController;

    // اضافه شدن CombatController برای رفع ارور کامپایل (باگ 04)
    private final CombatController combatController;

    public MainController(GameMap gameMap) {
        this.gameMap = gameMap;
        this.tribeController = new TribeController(gameMap);
        this.tribeController.spawnInitialTribes();
        this.economyController = new EconomyController(this);
        this.tradeController = new TradeController(gameMap);
        this.turnController = new TurnController(this, gameMap);
        this.unitController = new UnitController();
        this.buildController = new BuildController(gameMap);
        this.upgradeController = new UpgradeController(gameMap);
        this.saveLoadController = new SaveLoadController(this);
        this.combatController = new CombatController(gameMap);
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
    public CombatController getCombatController() { return combatController; }

    public Unit selectUnitAt(Hex hex) { return unitController.selectUnitAt(hex, gameMap); }
    public boolean canMove(Unit unit, Hex targetHex) { return unitController.canMove(unit, targetHex); }
    public void executeMove(Unit unit, Hex targetHex) { unitController.executeMove(unit, targetHex, gameMap); }

    public List<MenuAction> getTownHallMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        TownHall th = gameMap.getTownHall();

        boolean qEmpty = th.isProductionQueueEmpty();
        String prefix = qEmpty ? "" : "⏳ [BUSY] ";

        boolean isMilCapped = gameMap.getMilitaryUnitCount() >= gameMap.getMilitaryUnitCap();
        String milPrefix = isMilCapped ? "⚔️ [CAP REACHED] " : prefix;

        String whLabel = th.getLevel() >= 3
                ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TownHall Level %d", th.getLevel() + 1);
        actions.add(new MenuAction(whLabel, upgradeController.canAffordWarehouseUpgrade(),
                () -> upgradeController.handleWarehouseUpgrade()));

        actions.add(new MenuAction(th.isStoneMineUnlocked()
                ? "✅ ⛏️ Tech: Stone Mine"
                : String.format(prefix + "⛏️ Tech: Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"), () -> upgradeController.unlockTech("STONE_MINE")));

        actions.add(new MenuAction(th.isIronMineUnlocked()
                ? "✅ 🔩 Tech: Iron Mine"
                : String.format(prefix + "🔩 Tech: Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"), () -> upgradeController.unlockTech("IRON_MINE")));

        actions.add(new MenuAction(th.isProfessionalToolsUnlocked()
                ? "✅ 🔧 Tech: Steel Tools"
                : String.format(prefix + "🔧 Tech: Steel Tools (%dI)", GameConfig.TECH_STEEL_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"), () -> upgradeController.unlockTech("PROF_TOOLS")));

        actions.add(new MenuAction(th.isSeafaringUnlocked()
                ? "✅ ⛵ Tech: Seafaring"
                : String.format(prefix + "⛵ Tech: Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                upgradeController.canUnlockTech("SEAFARING"), () -> upgradeController.unlockTech("SEAFARING")));

        actions.add(new MenuAction(th.isDefensiveArchUnlocked()
                ? "✅ 🏰 Tech: Defensive Arch"
                : String.format(prefix + "🏰 Tech: Defensive Arch (%dS)", GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                upgradeController.canUnlockTech("DEFENSIVE_ARCH"), () -> upgradeController.unlockTech("DEFENSIVE_ARCH")));

        actions.add(new MenuAction(String.format(prefix + "👷 Train Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                upgradeController.canTrainUnit("WORKER"), () -> upgradeController.trainUnit("WORKER")));

        actions.add(new MenuAction(String.format(prefix + "🔨 Train Builder (%dF, %dW)", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                upgradeController.canTrainUnit("BUILDER"), () -> upgradeController.trainUnit("BUILDER")));

        actions.add(new MenuAction(String.format(prefix + "🧭 Train Explorer (%dF, %dW)", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                upgradeController.canTrainUnit("EXPLORER"), () -> upgradeController.trainUnit("EXPLORER")));

        actions.add(new MenuAction(milPrefix + "⚔️ Train Swordsman (20F, 10W)",
                upgradeController.canTrainUnit("SWORDSMAN"), () -> upgradeController.trainUnit("SWORDSMAN")));

        actions.add(new MenuAction(milPrefix + "🏹 Train Archer (20F, 20W) [Req: TH L2]",
                upgradeController.canTrainUnit("ARCHER"), () -> upgradeController.trainUnit("ARCHER")));

        actions.add(new MenuAction(milPrefix + "🏇 Train Cavalry (30F, 20I) [Req: TH L2 + Stable]",
                upgradeController.canTrainUnit("CAVALRY"), () -> upgradeController.trainUnit("CAVALRY")));

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
                actions.add(createBuildAction(builder, hex, BuildingType.LUMBER_MILL,  "🌲 Build Lumber Mill"));
                actions.add(createBuildAction(builder, hex, BuildingType.FARM,         "🌾 Build Farm"));
                actions.add(createBuildAction(builder, hex, BuildingType.STABLE,       "🐄 Build Stable"));
                actions.add(createBuildAction(builder, hex, BuildingType.STONE_MINE,   "⛏️ Build Stone Mine"));
                actions.add(createBuildAction(builder, hex, BuildingType.IRON_MINE,    "🔩 Build Iron Mine"));
                actions.add(createBuildAction(builder, hex, BuildingType.SETTLEMENT,   "🏘️ Build Settlement"));
                actions.add(createBuildAction(builder, hex, BuildingType.DOCK,         "⚓ Build Dock [Req: TH L2]"));
                actions.add(createBuildAction(builder, hex, BuildingType.MONUMENT,     "🏛️ Build Monument"));
                actions.add(createBuildAction(builder, hex, BuildingType.BAZAAR,       "⚖️ Build Bazaar [Req: TH L2]"));
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

        // بررسی و ایجاد دکمه حمله (باگ 04) - انطباق دقیق با نام متغیر selectedUnit و سازنده MenuAction
        boolean hasEnemyBuilding = hex.getBuilding() instanceof TribeCamp && !hex.getBuilding().isDestroyed();
        boolean hasEnemyUnit = gameMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == hex.getQ() && u.getR() == hex.getR() && (u.getClass().getSimpleName().equals("Bear") || u.getClass().getSimpleName().equals("Barbarian")));

        if (hasEnemyBuilding || hasEnemyUnit) {
            int dist = gameMap.getHexDistance(selectedUnit.getQ(), selectedUnit.getR(), hex.getQ(), hex.getR());
            boolean canAttack = selectedUnit.getCurrentAP() >= 1 && selectedUnit.getAttackRange() >= dist;
            String reason = canAttack ? "" : "Not enough AP or Target out of range";

            // استفاده از امضای صحیح سازنده MenuAction
            MenuAction attackAction = new MenuAction("⚔️ Attack Target (-1 AP)", canAttack, reason, () -> {
                java.util.List<Unit> attackers = new java.util.ArrayList<>();
                attackers.add(selectedUnit);
                boolean hasWall = hex.getBuilding() != null && hex.getBuilding().getMaxHp() > 100;
                combatController.executeAttack(
                        attackers, gameMap.getHexAt(selectedUnit.getQ(), selectedUnit.getR()), hex,
                        hasEnemyUnit && !hasEnemyBuilding, hasEnemyUnit, hasWall
                );
            });
            actions.add(attackAction);
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