package controller;

import model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MainController {

    private final GameMap gameMap;
    private final TurnController    turnController;
    private final UnitController    unitController;
    private final BuildController   buildController;
    private final UpgradeController upgradeController;
    private final EconomyController economyController;
    private final TradeController   tradeController;
    private final TribeController   tribeController;
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
    }

    // ─── Getters ──────────────────────────────────────────────────────────────
    public GameMap getGameMap()                  { return gameMap; }
    public TurnController getTurnController()    { return turnController; }
    public UnitController getUnitController()    { return unitController; }
    public BuildController getBuildController()  { return buildController; }
    public UpgradeController getUpgradeController() { return upgradeController; }
    public EconomyController getEconomyController() { return economyController; }
    public TradeController getTradeController()  { return tradeController; }
    public TribeController getTribeController()  { return tribeController; }
    public SaveLoadController getSaveLoadController() { return saveLoadController; }

    // ─── Unit helpers ─────────────────────────────────────────────────────────
    public Unit selectUnitAt(Hex hex) {
        return unitController.selectUnitAt(hex, gameMap);
    }

    /**
     * F-10: map حتماً باید پاس داده شود تا River، Road و Seafaring اثر کنند.
     */
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

        boolean qEmpty      = th.isProductionQueueEmpty();
        String  prefix      = qEmpty ? "" : "⏳ [BUSY] ";

        boolean isMilCapped = gameMap.getMilitaryUnitCount() >= gameMap.getMilitaryUnitCap();
        String  milPrefix   = isMilCapped ? "⚔️ [CAP REACHED] " : prefix;

        // ─── ارتقای TownHall ─────────────────────────────────────────────────
        String whLabel = th.getLevel() >= 3
                ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TownHall Level %d", th.getLevel() + 1);
        actions.add(new MenuAction(whLabel,
                upgradeController.canAffordWarehouseUpgrade(),
                () -> upgradeController.handleWarehouseUpgrade()));

        // ─── تکنولوژی‌ها ─────────────────────────────────────────────────────
        actions.add(new MenuAction(
                th.isStoneMineUnlocked()
                        ? "✅ ⛏️ Tech: Stone Mine"
                        : String.format(prefix + "⛏️ Tech: Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                upgradeController.canUnlockTech("STONE_MINE"),
                () -> upgradeController.unlockTech("STONE_MINE")));

        actions.add(new MenuAction(
                th.isIronMineUnlocked()
                        ? "✅ 🔩 Tech: Iron Mine"
                        : String.format(prefix + "🔩 Tech: Iron Mine (%dW, %dS)",
                        GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                upgradeController.canUnlockTech("IRON_MINE"),
                () -> upgradeController.unlockTech("IRON_MINE")));

        actions.add(new MenuAction(
                th.isProfessionalToolsUnlocked()
                        ? "✅ 🔧 Tech: Steel Tools"
                        : String.format(prefix + "🔧 Tech: Steel Tools (%dI)", GameConfig.TECH_STEEL_TOOLS_IRON),
                upgradeController.canUnlockTech("PROF_TOOLS"),
                () -> upgradeController.unlockTech("PROF_TOOLS")));

        actions.add(new MenuAction(
                th.isSeafaringUnlocked()
                        ? "✅ ⛵ Tech: Seafaring"
                        : String.format(prefix + "⛵ Tech: Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                upgradeController.canUnlockTech("SEAFARING"),
                () -> upgradeController.unlockTech("SEAFARING")));

        actions.add(new MenuAction(
                th.isDefensiveArchUnlocked()
                        ? "✅ 🏰 Tech: Defensive Arch"
                        : String.format(prefix + "🏰 Tech: Defensive Arch (%dS)",
                        GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                upgradeController.canUnlockTech("DEFENSIVE_ARCH"),
                () -> upgradeController.unlockTech("DEFENSIVE_ARCH")));

        // ─── یونیت‌های غیرنظامی ──────────────────────────────────────────────
        actions.add(new MenuAction(
                String.format(prefix + "👷 Train Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                upgradeController.canTrainUnit("WORKER"),
                () -> upgradeController.trainUnit("WORKER")));

        actions.add(new MenuAction(
                String.format(prefix + "🔨 Train Builder (%dF, %dW)",
                        GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                upgradeController.canTrainUnit("BUILDER"),
                () -> upgradeController.trainUnit("BUILDER")));

        actions.add(new MenuAction(
                String.format(prefix + "🧭 Train Explorer (%dF, %dW)",
                        GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                upgradeController.canTrainUnit("EXPLORER"),
                () -> upgradeController.trainUnit("EXPLORER")));

        // ─── یونیت‌های نظامی ─────────────────────────────────────────────────
        actions.add(new MenuAction(
                milPrefix + "⚔️ Train Swordsman (20F, 10W)",
                upgradeController.canTrainUnit("SWORDSMAN"),
                () -> upgradeController.trainUnit("SWORDSMAN")));

        actions.add(new MenuAction(
                milPrefix + "🏹 Train Archer (20F, 20W) [Req: TH L2]",
                upgradeController.canTrainUnit("ARCHER"),
                () -> upgradeController.trainUnit("ARCHER")));

        actions.add(new MenuAction(
                milPrefix + "🏇 Train Cavalry (30F, 20I) [Req: TH L2 + Stable]",
                upgradeController.canTrainUnit("CAVALRY"),
                () -> upgradeController.trainUnit("CAVALRY")));

        // ─── Save Manual ──────────────────────────────────────────────────────
        actions.add(new MenuAction("💾 Save Slot 1", true,
                () -> saveLoadController.saveGame("slot1")));
        actions.add(new MenuAction("💾 Save Slot 2", true,
                () -> saveLoadController.saveGame("slot2")));
        actions.add(new MenuAction("💾 Save Slot 3", true,
                () -> saveLoadController.saveGame("slot3")));

        return actions;
    }

    // ─── Unit menu ────────────────────────────────────────────────────────────
    /**
     * F-09: اگر selectedUnit روی hex متفاوتی از targetHex باشد و attack range داشته باشد،
     * این متد منوی حمله را می‌سازد. در غیر این‌صورت، منوی عادی (Build/Station) می‌سازد.
     */
    public List<MenuAction> getUnitMenuActions(Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();

        boolean isSameHex = (selectedUnit.getQ() == targetHex.getQ() &&
                selectedUnit.getR() == targetHex.getR());

        // ─── حمله (Attack) — targetHex != hex یونیت و یونیت برد حمله دارد ───
        if (!isSameHex && selectedUnit.getAttackRange() > 0) {
            return buildAttackMenu(selectedUnit, targetHex);
        }

        // ─── منوی Builder ────────────────────────────────────────────────────
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;

            if (targetHex.getBuilding() != null && !targetHex.getBuilding().isDestroyed()) {
                actions.add(new MenuAction("⛔ Hex already has a building", false, null));
            } else if (!targetHex.isInsideBorder()) {
                actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
            } else {
                actions.add(createBuildAction(builder, targetHex, BuildingType.LUMBER_MILL,  "🌲 Build Lumber Mill"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.FARM,          "🌾 Build Farm"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STABLE,        "🐄 Build Stable"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.STONE_MINE,    "⛏️ Build Stone Mine"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.IRON_MINE,     "🔩 Build Iron Mine"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.SETTLEMENT,    "🏘️ Build Settlement"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.DOCK,          "⚓ Build Dock [TH L2]"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.MONUMENT,      "🏛️ Build Monument"));
                actions.add(createBuildAction(builder, targetHex, BuildingType.BAZAAR,        "⚖️ Build Bazaar [TH L2]"));
            }

            // ─── منوی Worker ─────────────────────────────────────────────────────
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
                    boolean canStation = unitController.canStation(worker, targetHex);
                    actions.add(new MenuAction(
                            "⚙️ Station in " + b.getType().name(), canStation,
                            () -> unitController.handleStation(worker, targetHex)));
                } else {
                    actions.add(new MenuAction("⛔ No workable facility here", false, null));
                }
            }
        }

        return actions;
    }

    /**
     * ساخت منوی حمله برای یونیت نظامی که قصد حمله به targetHex را دارد.
     * شامل تمام یونیت‌های نظامی زنده روی hex مبدأ به عنوان گروه مهاجم.
     */
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

        // جمع‌آوری همه یونیت‌های نظامی زنده روی hex مبدأ
        List<Unit> attackers = gameMap.getUnits().stream()
                .filter(u -> u.isAlive()
                        && u.getQ() == selectedUnit.getQ()
                        && u.getR() == selectedUnit.getR()
                        && (u.getType() == UnitType.SWORDSMAN ||
                        u.getType() == UnitType.ARCHER    ||
                        u.getType() == UnitType.CAVALRY))
                .collect(Collectors.toList());

        // تشخیص نوع مدافع روی targetHex
        boolean hasAnimal = gameMap.getUnits().stream()
                .anyMatch(u -> u.isAlive()
                        && u.getQ() == targetHex.getQ()
                        && u.getR() == targetHex.getR()
                        && u.getType() == UnitType.BEAR);

        boolean hasTribeCampEnemy = (targetHex.getBuilding() instanceof TribeCamp)
                && ((TribeCamp) targetHex.getBuilding()).getTribe().getRelationship() <= -50;

        boolean isMilitaryTarget = hasAnimal || hasTribeCampEnemy;

        // بررسی دیوار (فقط برای dist == 1)
        boolean hasWall = false;
        if (dist == 1) {
            int dq  = targetHex.getQ() - sourceHex.getQ();
            int dr  = targetHex.getR() - sourceHex.getR();
            int dir = getAttackDirection(dq, dr);
            if (dir >= 0) hasWall = sourceHex.hasWall(dir);
        }

        // بررسی شرط‌های حمله
        boolean hasReadyAttacker = attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
        // از فاصله ۲ فقط Archer می‌تواند حمله کند
        boolean hasValidForDist  = (dist == 1) || attackers.stream()
                .anyMatch(u -> u.getType() == UnitType.ARCHER && u.getAttackRange() >= 2);

        boolean canAttack = !attackers.isEmpty() && hasReadyAttacker && hasValidForDist;

        // ساخت label گویا
        String typeLabel = isMilitaryTarget ? "🎲 Dice Combat" : "🏰 Siege";
        String wallLabel = (hasWall && dist == 1) ? " [🧱 Wall]" : "";
        String label     = String.format("⚔️ Attack! [%s] dist:%d%s", typeLabel, dist, wallLabel);

        // دلیل غیرفعال بودن
        String disabledReason;
        if (attackers.isEmpty()) {
            disabledReason = "No military units on source hex";
        } else if (!hasReadyAttacker) {
            disabledReason = "All attackers are out of AP";
        } else if (!hasValidForDist) {
            disabledReason = "No Archer available for range-2 attack";
        } else {
            disabledReason = "Ready";
        }

        final Hex finalSourceHex   = sourceHex;
        final boolean finalHasWall = hasWall;
        final boolean finalAnimal  = hasAnimal;
        final List<Unit> finalAtk  = attackers;

        actions.add(new MenuAction(label, canAttack, disabledReason, () -> {
            CombatController combatController = new CombatController(gameMap);
            combatController.executeAttack(
                    finalAtk, finalSourceHex, targetHex,
                    finalAnimal, false, finalHasWall);
            gameMap.removeDeadUnits();
            gameMap.updateFogOfWar();
        }));

        return actions;
    }

    /**
     * تبدیل (dq, dr) به index جهت (0-5) برای بررسی دیوار.
     * مطابق DIRECTIONS در GameMap: {1,0},{1,-1},{0,-1},{-1,0},{-1,1},{0,1}
     */
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