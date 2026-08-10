package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainController {

    private final GameMap gameMap;
    private final TurnController     turnController;
    private final UnitController     unitController;
    private final BuildController    buildController;
    private final UpgradeController  upgradeController;
    private final EconomyController  economyController;
    private final TradeController    tradeController;
    private final TribeController    tribeController;
    private final SaveLoadController saveLoadController;

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

        // F-01: spawn قبایل فقط برای بازی جدید.
        // اگر TribeCamp ای در نقشه وجود داشته باشد = بازی load شده → spawn نمی‌شود.
        boolean hasNoTribes = gameMap.getHexes().stream()
                .noneMatch(h -> h.getBuilding() instanceof TribeCamp);
        if (hasNoTribes) {
            tribeController.spawnInitialTribes();
        }
    }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public GameMap           getGameMap()            { return gameMap; }
    public TurnController    getTurnController()     { return turnController; }
    public UnitController    getUnitController()     { return unitController; }
    public BuildController   getBuildController()    { return buildController; }
    public UpgradeController getUpgradeController()  { return upgradeController; }
    public EconomyController getEconomyController()  { return economyController; }
    public TradeController   getTradeController()    { return tradeController; }
    public TribeController   getTribeController()    { return tribeController; }
    public SaveLoadController getSaveLoadController(){ return saveLoadController; }

    // ─── Unit helpers ─────────────────────────────────────────────────────────
    public Unit selectUnitAt(Hex hex) {
        return unitController.selectUnitAt(hex, gameMap);
    }

    /** F-10: map حتماً پاس داده می‌شود تا River، Road و Seafaring اثر کنند. */
    public boolean canMove(Unit unit, Hex targetHex) {
        return unitController.canMove(unit, targetHex, gameMap);
    }

    public void executeMove(Unit unit, Hex targetHex) {
        unitController.executeMove(unit, targetHex, gameMap);
    }

    // ─── TownHall menu ────────────────────────────────────────────────────────
    public List<MenuAction> getTownHallMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        TownHall th = gameMap.getTownHall();

        boolean qEmpty    = th.isProductionQueueEmpty();
        String  prefix    = qEmpty ? "" : "⏳ [BUSY] ";
        boolean isMilCap  = gameMap.getMilitaryUnitCount() >= gameMap.getMilitaryUnitCap();
        String  milPrefix = isMilCap ? "⚔️ [CAP] " : prefix;

        // ─── ارتقای TownHall ─────────────────────────────────────────────────
        String whLabel = th.getLevel() >= 3
                ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TH → Level %d", th.getLevel() + 1);
        actions.add(new MenuAction(whLabel,
                upgradeController.canAffordWarehouseUpgrade(),
                () -> upgradeController.handleWarehouseUpgrade()));

        // ─── تکنولوژی‌ها ─────────────────────────────────────────────────────
        actions.add(new MenuAction(
                th.isStoneMineUnlocked()
                        ? "✅ ⛏️ Tech: Stone Mine"
                        : String.format(prefix + "⛏️ Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"),
                () -> upgradeController.unlockTech("STONE_MINE")));

        actions.add(new MenuAction(
                th.isIronMineUnlocked()
                        ? "✅ 🔩 Tech: Iron Mine"
                        : String.format(prefix + "🔩 Iron Mine (%dW, %dS)",
                        GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"),
                () -> upgradeController.unlockTech("IRON_MINE")));

        actions.add(new MenuAction(
                th.isProfessionalToolsUnlocked()
                        ? "✅ 🔧 Tech: Steel Tools"
                        : String.format(prefix + "🔧 Steel Tools (%dI)", GameConfig.TECH_STEEL_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"),
                () -> upgradeController.unlockTech("PROF_TOOLS")));

        actions.add(new MenuAction(
                th.isSeafaringUnlocked()
                        ? "✅ ⛵ Tech: Seafaring"
                        : String.format(prefix + "⛵ Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                upgradeController.canUnlockTech("SEAFARING"),
                () -> upgradeController.unlockTech("SEAFARING")));

        actions.add(new MenuAction(
                th.isDefensiveArchUnlocked()
                        ? "✅ 🏰 Tech: Defensive Arch"
                        : String.format(prefix + "🏰 Defensive Arch (%dS)",
                        GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                upgradeController.canUnlockTech("DEFENSIVE_ARCH"),
                () -> upgradeController.unlockTech("DEFENSIVE_ARCH")));

        // ─── یونیت‌های غیرنظامی ──────────────────────────────────────────────
        actions.add(new MenuAction(
                String.format(prefix + "👷 Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                upgradeController.canTrainUnit("WORKER"),
                () -> upgradeController.trainUnit("WORKER")));

        actions.add(new MenuAction(
                String.format(prefix + "🔨 Builder (%dF, %dW)",
                        GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                upgradeController.canTrainUnit("BUILDER"),
                () -> upgradeController.trainUnit("BUILDER")));

        actions.add(new MenuAction(
                String.format(prefix + "🧭 Explorer (%dF, %dW)",
                        GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                upgradeController.canTrainUnit("EXPLORER"),
                () -> upgradeController.trainUnit("EXPLORER")));

        // ─── یونیت‌های نظامی ─────────────────────────────────────────────────
        actions.add(new MenuAction(
                milPrefix + "⚔️ Swordsman (20F, 10W)",
                upgradeController.canTrainUnit("SWORDSMAN"),
                () -> upgradeController.trainUnit("SWORDSMAN")));

        actions.add(new MenuAction(
                milPrefix + "🏹 Archer (20F, 20W) [TH L2]",
                upgradeController.canTrainUnit("ARCHER"),
                () -> upgradeController.trainUnit("ARCHER")));

        actions.add(new MenuAction(
                milPrefix + "🏇 Cavalry (30F, 20I) [TH L2 + Stable]",
                upgradeController.canTrainUnit("CAVALRY"),
                () -> upgradeController.trainUnit("CAVALRY")));

        // ─── Save ────────────────────────────────────────────────────────────
        actions.add(new MenuAction("💾 Save Slot 1", true,
                () -> saveLoadController.saveGame("slot1")));
        actions.add(new MenuAction("💾 Save Slot 2", true,
                () -> saveLoadController.saveGame("slot2")));
        actions.add(new MenuAction("💾 Save Slot 3", true,
                () -> saveLoadController.saveGame("slot3")));

        return actions;
    }

    // ─── Unit menu ────────────────────────────────────────────────────────────
    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();

        boolean isSameHex = (selectedUnit.getQ() == targetHex.getQ()
                && selectedUnit.getR() == targetHex.getR());

        // حمله: یونیت نظامی روی hex دیگری
        if (!isSameHex && selectedUnit.getAttackRange() > 0) {
            return buildAttackMenu(selectedUnit, targetHex);
        }

        // منوی Builder
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;
            if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
                actions.add(new MenuAction("⛔ Hex already has a building", false, null));
            } else if (!targetHex.isInsideBorder()) {
                actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
            } else {
                actions.add(createBuildAction(builder, targetHex, BuildingType.LUMBER_MILL, "🌲 Lumber Mill"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.FARM,        "🌾 Farm"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STABLE,      "🐄 Stable"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STONE_MINE,  "⛏️ Stone Mine"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.IRON_MINE,   "🔩 Iron Mine"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.SETTLEMENT,  "🏘️ Settlement"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.DOCK,        "⚓ Dock [TH L2]"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.MONUMENT,    "🏛️ Monument"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.BAZAAR,      "⚖️ Bazaar [TH L2]"));
            }

            // منوی Worker
        } else if (selectedUnit.getType() == UnitType.WORKER) {
            Worker worker = (Worker) selectedUnit;
            if (worker.isStationed()) {
                actions.add(new MenuAction("🚪 Leave Facility",
                        unitController.canEject(worker),
                        () -> unitController.handleEject(worker)));
            } else {
                Building b = targetHex.getBuilding();
                if (b != null && !b.isDestroyed()
                        && b.getType() != BuildingType.TOWN_HALL
                        && b.getType() != BuildingType.MONUMENT
                        && b.getMaxWorkers() > 0) {
                    boolean can = unitController.canStation(worker, targetHex);
                    actions.add(new MenuAction("⚙️ Station in " + b.getType().name(), can,
                            () -> unitController.handleStation(worker, targetHex)));
                } else {
                    actions.add(new MenuAction("⛔ No workable facility here", false, null));
                }
            }
        }

        return actions;
    }

    private List<MenuAction> buildAttackMenu(Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        Hex sourceHex = gameMap.getHexAt(selectedUnit.getQ(), selectedUnit.getR());
        if (sourceHex == null) return actions;

        int dist = gameMap.getHexDistance(
                selectedUnit.getQ(), selectedUnit.getR(),
                targetHex.getQ(), targetHex.getR());

        if (dist < 1 || dist > 2) {
            actions.add(new MenuAction("⛔ Target out of range (max 2)", false, null));
            return actions;
        }

        List<Unit> attackers = gameMap.getUnits().stream()
                .filter(u -> u.isAlive()
                        && u.getQ() == selectedUnit.getQ()
                        && u.getR() == selectedUnit.getR()
                        && (u.getType() == UnitType.SWORDSMAN
                        || u.getType() == UnitType.ARCHER
                        || u.getType() == UnitType.CAVALRY))
                .collect(Collectors.toList());

        boolean hasAnimal = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == targetHex.getQ()
                        && u.getR() == targetHex.getR()
                        && u.getType() == UnitType.BEAR);

        boolean hasTribeEnemy = (targetHex.getBuilding() instanceof TribeCamp)
                && ((TribeCamp) targetHex.getBuilding()).getTribe().getRelationship() <= -50;

        boolean isMilTarget = hasAnimal || hasTribeEnemy;

        boolean hasWall = false;
        if (dist == 1) {
            int dq  = targetHex.getQ() - sourceHex.getQ();
            int dr  = targetHex.getR() - sourceHex.getR();
            int dir = getAttackDirection(dq, dr);
            if (dir >= 0) hasWall = sourceHex.hasWall(dir);
        }

        boolean hasReadyAttacker = attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
        boolean hasValidForDist  = (dist == 1) || attackers.stream()
                .anyMatch(u -> u.getType() == UnitType.ARCHER && u.getAttackRange() >= 2);
        boolean canAttack = !attackers.isEmpty() && hasReadyAttacker && hasValidForDist;

        String typeLabel = isMilTarget ? "🎲 Dice" : "🏰 Siege";
        String wallLabel = (hasWall && dist == 1) ? " [🧱 Wall]" : "";
        String label     = String.format("⚔️ Attack! [%s] dist:%d%s", typeLabel, dist, wallLabel);

        String disabledReason;
        if (attackers.isEmpty())        disabledReason = "No military units on source hex";
        else if (!hasReadyAttacker)     disabledReason = "All attackers out of AP";
        else if (!hasValidForDist)      disabledReason = "No Archer for range-2 attack";
        else                            disabledReason = "Ready";

        final Hex      fSource  = sourceHex;
        final boolean  fWall    = hasWall;
        final boolean  fAnimal  = hasAnimal;
        final List<Unit> fAtk   = attackers;

        actions.add(new MenuAction(label, canAttack, disabledReason, () -> {
            CombatController cc = new CombatController(gameMap);
            cc.executeAttack(fAtk, fSource, targetHex, fAnimal, false, fWall);
            gameMap.removeDeadUnits();
            gameMap.updateFogOfWar();
        }));

        return actions;
    }

    private int getAttackDirection(int dq, int dr) {
        if (dq ==  1 && dr ==  0) return 0;
        if (dq ==  1 && dr == -1) return 1;
        if (dq ==  0 && dr == -1) return 2;
        if (dq == -1 && dr ==  0) return 3;
        if (dq == -1 && dr ==  1) return 4;
        if (dq ==  0 && dr ==  1) return 5;
        return -1;
    }

    private MenuAction createBuildAction(Builder builder, Hex hex, BuildingType type, String label) {
        boolean canBuild = buildController.canBuild(type, hex, builder);
        return new MenuAction(label + " (-" + type.getApCost() + "AP)", canBuild, () -> {
            buildController.buildStructure(builder, type, hex);
            GameEventDispatcher.fireUnitStateChanged(builder);
        });
    }
}