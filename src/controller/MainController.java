package controller;

import model.*;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    // I3: flag برای اطلاع‌رسانی به Pause Menu که Save در این لحظه مجاز نیست
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

    // ─── Getters ──────────────────────────────────────────────────────────────
    public GameMap            getGameMap()             { return gameMap; }
    public TurnController     getTurnController()      { return turnController; }
    public UnitController     getUnitController()      { return unitController; }
    public BuildController    getBuildController()     { return buildController; }
    public UpgradeController  getUpgradeController()   { return upgradeController; }
    public EconomyController  getEconomyController()   { return economyController; }
    public TradeController    getTradeController()     { return tradeController; }
    public TribeController    getTribeController()     { return tribeController; }
    public SaveLoadController getSaveLoadController()  { return saveLoadController; }

    // I3: getter/setter برای processing flag
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

    public List<MenuAction> getTownHallMenuActions() {
        List<MenuAction> actions = new ArrayList<>();
        TownHall th = gameMap.getTownHall();

        boolean qEmpty    = th.isProductionQueueEmpty();
        String  prefix    = qEmpty ? "" : "⏳ [BUSY] ";
        boolean isMilCap  = gameMap.getMilitaryUnitCount() >= gameMap.getMilitaryUnitCap();
        String  milPrefix = isMilCap ? "⚔️ [CAP] " : prefix;

        if (!qEmpty) {
            actions.add(new MenuAction("🚫 Cancel Current Production (No Refund)", true, () -> th.cancelCurrentProduction())
                    .setConfirmation("Are you sure you want to cancel the current production?\n\n⚠️ ALL INVESTED RESOURCES WILL BE LOST!"));
        }

        String whLabel = th.getLevel() >= 3 ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TH → Level %d", th.getLevel() + 1);
        actions.add(new MenuAction(whLabel, upgradeController.canAffordWarehouseUpgrade(),
                () -> upgradeController.handleWarehouseUpgrade()));

        actions.add(new MenuAction(th.isStoneMineUnlocked() ? "✅ ⛏️ Tech: Stone Mine"
                : String.format(prefix + "⛏️ Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"),
                () -> upgradeController.unlockTech("STONE_MINE")));

        actions.add(new MenuAction(th.isIronMineUnlocked() ? "✅ 🔩 Tech: Iron Mine"
                : String.format(prefix + "🔩 Iron Mine (%dW, %dS)",
                GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"),
                () -> upgradeController.unlockTech("IRON_MINE")));

        actions.add(new MenuAction(th.isProfessionalToolsUnlocked() ? "✅ 🔧 Tech: Steel Tools"
                : String.format(prefix + "🔧 Steel Tools (%dI)", GameConfig.TECH_STEEL_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"),
                () -> upgradeController.unlockTech("PROF_TOOLS")));

        actions.add(new MenuAction(th.isSeafaringUnlocked() ? "✅ ⛵ Tech: Seafaring"
                : String.format(prefix + "⛵ Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                upgradeController.canUnlockTech("SEAFARING"),
                () -> upgradeController.unlockTech("SEAFARING")));

        actions.add(new MenuAction(th.isDefensiveArchUnlocked() ? "✅ 🏰 Tech: Defensive Arch"
                : String.format(prefix + "🏰 Defensive Arch (%dS)", GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                upgradeController.canUnlockTech("DEFENSIVE_ARCH"),
                () -> upgradeController.unlockTech("DEFENSIVE_ARCH")));

        actions.add(new MenuAction(String.format(prefix + "👷 Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                upgradeController.canTrainUnit("WORKER"), () -> upgradeController.trainUnit("WORKER")));
        actions.add(new MenuAction(String.format(prefix + "🔨 Builder (%dF, %dW)",
                GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                upgradeController.canTrainUnit("BUILDER"), () -> upgradeController.trainUnit("BUILDER")));
        actions.add(new MenuAction(String.format(prefix + "🧭 Explorer (%dF, %dW)",
                GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                upgradeController.canTrainUnit("EXPLORER"), () -> upgradeController.trainUnit("EXPLORER")));

        actions.add(new MenuAction(milPrefix + "⚔️ Swordsman (20F, 10W)",
                upgradeController.canTrainUnit("SWORDSMAN"), () -> upgradeController.trainUnit("SWORDSMAN")));
        actions.add(new MenuAction(milPrefix + "🏹 Archer (20F, 20W) [TH L2]",
                upgradeController.canTrainUnit("ARCHER"), () -> upgradeController.trainUnit("ARCHER")));

        return actions;
    }

    // اصلاح باگ [B1]: جایگزینی کامل متد برای اضافه کردن دلیل غیرفعال بودن و اصلاح پیشوندها
    public List<MenuAction> getStableMenuActions() {
        TownHall th = gameMap.getTownHall();
        boolean qEmpty   = th.isProductionQueueEmpty();
        boolean isMilCap = gameMap.getMilitaryUnitCount() >= gameMap.getMilitaryUnitCap();
        String  prefix   = (isMilCap ? "⚔️ [CAP] " : "") + (!qEmpty ? "⏳ [BUSY] " : "");

        return List.of(new MenuAction(
                prefix + "🏇 Train Cavalry (30F, 20I) [TH L2 + Stable]",
                upgradeController.canTrainUnit("CAVALRY"),
                "Requires TH Level 2 + active Stable + unit cap not full + resources",
                () -> upgradeController.trainUnit("CAVALRY")
        ));
    }

    public List<MenuAction> getBazaarMenuActions(Bazaar bazaar) {
        List<MenuAction> actions = new ArrayList<>();
        int level = bazaar.getLevel();

        int amount = (level == 1) ? 10 : (level == 2) ? 100 : 500;
        String prefix = bazaar.hasTraded() ? "🚫 [Traded] " : "💱 ";

        if (bazaar.canUpgrade()) {
            actions.add(new MenuAction("⬆️ Upgrade Bazaar to Level " + (level + 1), true, () -> {
                bazaar.upgrade();
                GameEventDispatcher.fireNotification("Bazaar upgraded to Level " + bazaar.getLevel() + "!");
            }));
        }

        if (bazaar.hasTraded()) {
            actions.add(new MenuAction(prefix + "Already traded this turn", false, null));
            return actions;
        }

        ResourceType[] types = {ResourceType.FOOD, ResourceType.WOOD, ResourceType.STONE, ResourceType.IRON};
        String[] icons = {"🍔", "🪵", "🪨", "⚙️"};
        Inventory inv = gameMap.getTownHall().getInventory();

        for (int i = 0; i < types.length; i++) {
            for (int j = 0; j < types.length; j++) {
                if (i == j) continue;
                ResourceType give = types[i];
                ResourceType get = types[j];
                String giveLabel = icons[i] + " " + give.name();
                String getLabel = icons[j] + " " + get.name();
                boolean canAfford = inv.hasEnough(give, amount);

                actions.add(new MenuAction(String.format("%s Trade %d %s ➔ %s", prefix, amount, giveLabel, getLabel), canAfford, () -> {
                    if (tradeController.tradeWithBazaar(bazaar, give, get)) {
                        GameEventDispatcher.fireNotification("Trade successful!");
                    } else {
                        GameEventDispatcher.fireNotification("Trade failed. Storage full or resources missing.");
                    }
                }));
            }
        }
        return actions;
    }

    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        boolean isSameHex = (selectedUnit.getQ() == targetHex.getQ() && selectedUnit.getR() == targetHex.getR());
        if (!isSameHex && selectedUnit.getAttackRange() > 0) {
            return buildAttackMenu(selectedUnit, targetHex);
        }
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;
            Building existing = targetHex.getBuilding();
            if (existing != null && !existing.isDestroyed()) {
                boolean canDestroy = buildController.canDestroy(targetHex, "BUILDING", 0, builder);
                BuildingType bType = existing.getType();
                actions.add(new MenuAction("🗑️ Destroy " + bType.name() + " (-1 AP, no refund)",
                        canDestroy,
                        getDestroyDisabledReason(bType, builder),
                        () -> buildController.destroyStructure(builder, targetHex, "BUILDING", 0))
                        .setConfirmation("Destroy " + bType.name() + "?\n\n⚠️ No resources will be refunded.\nWorkers inside will be relocated."));
            } else if (!targetHex.isInsideBorder()) {
                actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
            } else {
                actions.add(createBuildAction(builder, targetHex, BuildingType.LUMBER_MILL, "🌲 Lumber Mill"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.FARM,        "🌾 Farm"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STABLE,      "🐄 Stable"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STONE_MINE,  "⛏️ Stone Mine"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.IRON_MINE,   "🔩 Iron Mine"));

                // [N2] Fix: اضافه شدن هشدار واضح برای جریمه Happiness به لیبل Settlement
                actions.add(createBuildAction(builder, targetHex, BuildingType.SETTLEMENT,  "🏘️ Settlement (⚠️ -1 Happiness)"));

                boolean hasDockDiscount = gameMap.getTownHall().getDiscountedDocks() > 0;
                String dockLabel = hasDockDiscount ? "⚓ Dock [🎉 FREE by Mission!]" : "⚓ Dock [TH L2]";
                actions.add(createBuildAction(builder, targetHex, BuildingType.DOCK, dockLabel));
                actions.add(createBuildAction(builder, targetHex, BuildingType.MONUMENT, "🏛️ Monument"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.BAZAAR,   "⚖️ Bazaar [TH L2]"));
            }
        } else if (selectedUnit.getType() == UnitType.WORKER) {
            Worker worker = (Worker) selectedUnit;
            if (worker.isStationed()) {
                actions.add(new MenuAction("🚪 Leave Facility",
                        unitController.canEject(worker),
                        () -> unitController.handleEject(worker)));
            } else {
                Building b = targetHex.getBuilding();
                if (b != null && !b.isDestroyed() && b.getType() != BuildingType.TOWN_HALL
                        && b.getType() != BuildingType.MONUMENT && b.getMaxWorkers() > 0) {
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

    private String getDestroyDisabledReason(BuildingType type, Builder builder) {
        if (type == BuildingType.TOWN_HALL)    return "Cannot destroy Town Hall";
        if (type == BuildingType.TRIBE_CAMP)   return "Cannot destroy Tribe Camp";
        if (type == BuildingType.TRADING_POST) return "Cannot destroy Trading Post";
        if (builder.getCurrentAP() < 1)        return "Not enough AP (need 1)";
        return "Builder must be on or adjacent to the hex";
    }

    private List<MenuAction> buildAttackMenu(Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        Hex sourceHex = gameMap.getHexAt(selectedUnit.getQ(), selectedUnit.getR());
        if (sourceHex == null) return actions;
        int dist = gameMap.getHexDistance(selectedUnit.getQ(), selectedUnit.getR(), targetHex.getQ(), targetHex.getR());
        if (dist < 1 || dist > 2) {
            actions.add(new MenuAction("⛔ Target out of range (max 2)", false, null));
            return actions;
        }
        List<Unit> attackers = gameMap.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == selectedUnit.getQ() && u.getR() == selectedUnit.getR()
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY))
                .collect(Collectors.toList());
        boolean hasAnyEnemy = isHostile(targetHex);
        boolean isMilTarget = hasAnyEnemy;
        boolean hasWall = false;
        if (dist == 1) {
            int dir = getAttackDirection(targetHex.getQ() - sourceHex.getQ(), targetHex.getR() - sourceHex.getR());
            if (dir >= 0) hasWall = sourceHex.hasWall(dir);
        }
        if (hasAnyEnemy) {
            boolean hasReadyAttacker = attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
            boolean hasValidForDist  = (dist == 1) || attackers.stream().anyMatch(u -> u.getType() == UnitType.ARCHER && u.getAttackRange() >= 2);
            boolean canAttack = !attackers.isEmpty() && hasReadyAttacker && hasValidForDist;
            final boolean fAnimal    = gameMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && u.getType() == UnitType.BEAR);
            final boolean fEnemyUnit = gameMap.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && u.isEnemy());
            final boolean fSiege = !(fAnimal || fEnemyUnit);
            String typeLabel = fSiege ? "🏰 Siege" : "🎲 Dice";
            String wallLabel = (hasWall && dist == 1 && !fSiege) ? " [🧱 Wall +2 def]" : "";
            String label     = String.format("⚔️ Attack! [%s] dist:%d%s", typeLabel, dist, wallLabel);
            String disabledReason;
            if (attackers.isEmpty())    disabledReason = "No military units on source hex";
            else if (!hasReadyAttacker) disabledReason = "All attackers out of AP";
            else if (!hasValidForDist)  disabledReason = "No Archer for range-2 attack";
            else                        disabledReason = "Ready";
            final Hex         fSource = sourceHex;
            final boolean     fWall   = hasWall;
            final List<Unit>  fAtk    = attackers;
            actions.add(new MenuAction(label, canAttack, disabledReason, () -> {
                CombatController cc = new CombatController(gameMap);
                cc.executeAttack(fAtk, fSource, targetHex, fSiege, fAnimal, fWall);
                gameMap.removeDeadUnits();
                gameMap.updateFogOfWar();
            }));
            if (hasWall && isMilTarget && dist == 1) {
                actions.add(new MenuAction("⚔️ Attack Wall [🏰 Siege]", canAttack, disabledReason, () -> {
                    CombatController cc = new CombatController(gameMap);
                    cc.executeAttack(fAtk, fSource, targetHex, true, false, true);
                    gameMap.removeDeadUnits();
                    gameMap.updateFogOfWar();
                }));
            }
        }
        if (isCapturable(selectedUnit, targetHex)) {
            boolean canCapture = !attackers.isEmpty() && attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
            actions.add(new MenuAction("🏴 Capture Hex (1 AP)", canCapture,
                    canCapture ? "No defenders — seize this hex" : "Need military unit with AP ≥ 1",
                    () -> {
                        attackers.stream().filter(u -> u.getCurrentAP() >= 1).findFirst().ifPresent(u -> u.consumeAP(1));
                        targetHex.setInsideBorder(true);
                        targetHex.setExplored(true);
                        gameMap.updateFogOfWar();
                        GameEventDispatcher.fireBorderExpanded(targetHex.getQ(), targetHex.getR());
                    }));
        }
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