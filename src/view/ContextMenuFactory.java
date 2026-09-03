package view;

import com.google.gson.Gson;                          // ← NEW import
import controller.CombatController;
import controller.MainController;
import controller.MenuAction;
import model.*;
import network.client.NetworkManager;                 // ← NEW import
import network.messages.game.AttackRequest;           // ← NEW import

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContextMenuFactory {

    public static List<MenuAction> buildTownHallMenu(MainController mc) {
        List<MenuAction> actions = new ArrayList<>();
        GameMap map = mc.getGameMap();
        TownHall th = map.getTownHall();

        boolean qEmpty    = th.isProductionQueueEmpty();
        String  prefix    = qEmpty ? "" : "⏳ [BUSY] ";
        boolean isMilCap  = map.getMilitaryUnitCount() >= map.getMilitaryUnitCap();
        String  milPrefix = isMilCap ? "⚔️ [CAP] " : prefix;

        if (!qEmpty) {
            actions.add(new MenuAction("🚫 Cancel Current Production (No Refund)", true, th::cancelCurrentProduction)
                    .setConfirmation("Are you sure you want to cancel the current production?\n\n⚠️ ALL INVESTED RESOURCES WILL BE LOST!"));
        }

        String whLabel = th.getLevel() >= 3 ? "✅ Capital MAXED"
                : String.format(prefix + "📦 Upgrade TH → Level %d", th.getLevel() + 1);
        actions.add(new MenuAction(whLabel, mc.getUpgradeController().canAffordWarehouseUpgrade(),
                () -> mc.getUpgradeController().handleWarehouseUpgrade()));

        actions.add(new MenuAction(th.isStoneMineUnlocked() ? "✅ ⛏️ Tech: Stone Mine"
                : String.format(prefix + "⛏️ Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD),
                mc.getUpgradeController().canUnlockTech("STONE_MINE"),
                () -> mc.getUpgradeController().unlockTech("STONE_MINE")));

        actions.add(new MenuAction(th.isIronMineUnlocked() ? "✅ 🔩 Tech: Iron Mine"
                : String.format(prefix + "🔩 Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE),
                mc.getUpgradeController().canUnlockTech("IRON_MINE"),
                () -> mc.getUpgradeController().unlockTech("IRON_MINE")));

        actions.add(new MenuAction(th.isProfessionalToolsUnlocked() ? "✅ 🔧 Tech: Steel Tools"
                : String.format(prefix + "🔧 Steel Tools (%dI) [Requires Iron Mine tech]", GameConfig.TECH_STEEL_TOOLS_IRON),
                mc.getUpgradeController().canUnlockTech("PROF_TOOLS"),
                "Requires TH Lv2 + Iron Mine tech + " + GameConfig.TECH_STEEL_TOOLS_IRON + " Iron",
                () -> mc.getUpgradeController().unlockTech("PROF_TOOLS")));

        actions.add(new MenuAction(th.isSeafaringUnlocked() ? "✅ ⛵ Tech: Seafaring"
                : String.format(prefix + "⛵ Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD),
                mc.getUpgradeController().canUnlockTech("SEAFARING"),
                () -> mc.getUpgradeController().unlockTech("SEAFARING")));

        actions.add(new MenuAction(th.isDefensiveArchUnlocked() ? "✅ 🏰 Tech: Defensive Arch"
                : String.format(prefix + "🏰 Defensive Arch (%dS)", GameConfig.TECH_DEFENSIVE_ARCH_STONE),
                mc.getUpgradeController().canUnlockTech("DEFENSIVE_ARCH"),
                () -> mc.getUpgradeController().unlockTech("DEFENSIVE_ARCH")));

        actions.add(new MenuAction(String.format(prefix + "👷 Worker (%dF)", GameConfig.WORKER_FOOD_COST),
                mc.getUpgradeController().canTrainUnit("WORKER"), () -> mc.getUpgradeController().trainUnit("WORKER")));
        actions.add(new MenuAction(String.format(prefix + "🔨 Builder (%dF, %dW)", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST),
                mc.getUpgradeController().canTrainUnit("BUILDER"), () -> mc.getUpgradeController().trainUnit("BUILDER")));
        actions.add(new MenuAction(String.format(prefix + "🧭 Explorer (%dF, %dW)", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST),
                mc.getUpgradeController().canTrainUnit("EXPLORER"), () -> mc.getUpgradeController().trainUnit("EXPLORER")));

        actions.add(new MenuAction(milPrefix + "⚔️ Swordsman (20F, 10W)",
                mc.getUpgradeController().canTrainUnit("SWORDSMAN"), () -> mc.getUpgradeController().trainUnit("SWORDSMAN")));
        actions.add(new MenuAction(milPrefix + "🏹 Archer (20F, 20W) [TH L2]",
                mc.getUpgradeController().canTrainUnit("ARCHER"), () -> mc.getUpgradeController().trainUnit("ARCHER")));

        return actions;
    }

    public static List<MenuAction> buildStableMenu(MainController mc) {
        GameMap map = mc.getGameMap();
        TownHall th = map.getTownHall();
        boolean qEmpty   = th.isProductionQueueEmpty();
        boolean isMilCap = map.getMilitaryUnitCount() >= map.getMilitaryUnitCap();
        String  prefix   = (isMilCap ? "⚔️ [CAP] " : "") + (!qEmpty ? "⏳ [BUSY] " : "");

        return List.of(new MenuAction(
                prefix + "🏇 Train Cavalry (30F, 20I) [TH L2 + Stable]",
                mc.getUpgradeController().canTrainUnit("CAVALRY"),
                "Requires TH Level 2 + active Stable + unit cap not full + resources",
                () -> mc.getUpgradeController().trainUnit("CAVALRY")
        ));
    }

    public static List<MenuAction> buildBazaarMenu(MainController mc, Bazaar bazaar, Runnable onTradeAction) {
        List<MenuAction> actions = new ArrayList<>();
        boolean traded = bazaar.hasTraded();
        int     level  = bazaar.getLevel();

        actions.add(new MenuAction("⚖️ Trade (Level " + level + ")", !traded, "Already traded this turn", onTradeAction));

        if (bazaar.canUpgrade()) {
            int stoneCost = (level == 1) ? 30 : 60;
            boolean canUpg = mc.getTradeController().canUpgradeBazaar(bazaar);
            actions.add(new MenuAction("⬆️ Upgrade Bazaar → Level " + (level + 1) + " (" + stoneCost + " Stone)",
                    canUpg, "Need " + stoneCost + " Stone to upgrade", () -> {
                mc.getTradeController().upgradeBazaar(bazaar);
            }));
        } else {
            actions.add(new MenuAction("✅ Bazaar is at Max Level (3)", false, null));
        }

        return actions;
    }

    public static List<MenuAction> buildTradingPostMenu(MainController mc, TradingPost post, Hex hex, Runnable onTradeAction) {
        List<MenuAction> actions = new ArrayList<>();

        if (!hex.isInsideBorder()) {
            actions.add(new MenuAction("⛔ Hex must be in your territory to trade", false, null));
            return actions;
        }

        boolean traded = post.hasTraded();
        actions.add(new MenuAction("🏪 Trade (80% Rate - Custom Amount)", !traded, "Already traded this turn", onTradeAction));

        return actions;
    }

    public static List<MenuAction> buildUnitMenu(MainController mc, Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        boolean isSameHex = (selectedUnit.getQ() == targetHex.getQ() && selectedUnit.getR() == targetHex.getR());
        if (!isSameHex && selectedUnit.getAttackRange() > 0) {
            return buildAttackMenu(mc, selectedUnit, targetHex);
        }
        if (selectedUnit.getType() == UnitType.BUILDER) {
            Builder builder = (Builder) selectedUnit;
            Building existing = targetHex.getBuilding();
            if (existing != null && !existing.isDestroyed()) {
                boolean canDestroy = mc.getBuildController().canDestroy(targetHex, "BUILDING", 0, builder);
                BuildingType bType = existing.getType();
                actions.add(new MenuAction("🗑️ Destroy " + bType.name() + " (-1 AP, no refund)",
                        canDestroy, getDestroyDisabledReason(bType, builder),
                        () -> mc.getBuildController().destroyStructure(builder, targetHex, "BUILDING", 0))
                        .setConfirmation("Destroy " + bType.name() + "?\n\n⚠️ No resources will be refunded.\nWorkers inside will be relocated."));
            } else if (!targetHex.isInsideBorder()) {
                actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
            } else {
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.LUMBER_MILL, "🌲 Lumber Mill"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.FARM,        "🌾 Farm"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.STABLE,      "🐄 Stable"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.STONE_MINE,  "⛏️ Stone Mine"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.IRON_MINE,   "🔩 Iron Mine"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.SETTLEMENT,  "🏘️ Settlement (⚠️ -1 Happiness)"));

                boolean hasDockDiscount = mc.getGameMap().getTownHall().getDiscountedDocks() > 0;
                String dockLabel = hasDockDiscount ? "⚓ Dock [🎉 FREE by Mission!]" : "⚓ Dock [TH L2]";
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.DOCK, dockLabel));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.MONUMENT, "🏛️ Monument"));
                actions.add(createBuildAction(mc, builder, targetHex, BuildingType.BAZAAR,   "⚖️ Bazaar [TH L2]"));
            }
        } else if (selectedUnit.getType() == UnitType.WORKER) {
            Worker worker = (Worker) selectedUnit;
            if (worker.isStationed()) {
                actions.add(new MenuAction("🚪 Leave Facility", mc.getUnitController().canEject(worker), () -> mc.getUnitController().handleEject(worker)));
            } else {
                Building b = targetHex.getBuilding();
                if (b != null && !b.isDestroyed() && b.getType() != BuildingType.TOWN_HALL
                        && b.getType() != BuildingType.MONUMENT && b.getMaxWorkers() > 0) {

                    boolean can = mc.getUnitController().canStation(worker, targetHex, mc.getGameMap());
                    actions.add(new MenuAction("⚙️ Station in " + b.getType().name(), can,
                            () -> mc.getUnitController().handleStation(worker, targetHex, mc.getGameMap())));
                } else {
                    actions.add(new MenuAction("⛔ No workable facility here", false, null));
                }
            }
        }
        return actions;
    }

    private static MenuAction createBuildAction(MainController mc, Builder builder, Hex hex, BuildingType type, String label) {
        boolean canBuild = mc.getBuildController().canBuild(type, hex, builder);
        return new MenuAction(label + " (-" + type.getApCost() + "AP)", canBuild, () -> {
            mc.getBuildController().buildStructure(builder, type, hex);
            GameEventDispatcher.fireUnitStateChanged(builder);
        });
    }

    // ─── Attack Menu — B9 fix ─────────────────────────────────────────────────

    private static List<MenuAction> buildAttackMenu(MainController mc, Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        GameMap map = mc.getGameMap();
        Hex sourceHex = map.getHexAt(selectedUnit.getQ(), selectedUnit.getR());
        if (sourceHex == null) return actions;

        int dist = map.getHexDistance(selectedUnit.getQ(), selectedUnit.getR(),
                targetHex.getQ(), targetHex.getR());
        if (dist < 1 || dist > 2) {
            actions.add(new MenuAction("⛔ Target out of range (max 2)", false, null));
            return actions;
        }

        List<Unit> attackers = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == selectedUnit.getQ() && u.getR() == selectedUnit.getR()
                        && (u.getType() == UnitType.SWORDSMAN || u.getType() == UnitType.ARCHER || u.getType() == UnitType.CAVALRY))
                .collect(Collectors.toList());

        boolean hasAnyEnemy = mc.isAttackable(targetHex);

        boolean tempHasWall = false;
        if (dist == 1) {
            int dir = getAttackDirection(map, sourceHex, targetHex);
            if (dir >= 0) tempHasWall = sourceHex.hasWall(dir);
        }
        final boolean hasWall = tempHasWall;

        // Determine the NetworkManager (null = single-player, non-null = multiplayer)
        final NetworkManager nm = mc.getNetworkManager();
        final Gson gson = new Gson();

        if (hasAnyEnemy) {
            boolean hasReadyAttacker = attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
            boolean hasValidForDist  = (dist == 1) || attackers.stream()
                    .anyMatch(u -> u.getType() == UnitType.ARCHER && u.getAttackRange() >= 2);
            boolean canAttack = !attackers.isEmpty() && hasReadyAttacker && hasValidForDist;

            final boolean fAnimal    = map.getUnits().stream().anyMatch(u ->
                    u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                            && u.getType() == UnitType.BEAR);
            final boolean fEnemyUnit = map.getUnits().stream().anyMatch(u ->
                    u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                            && u.isEnemy());
            final boolean fSiege = !(fAnimal || fEnemyUnit);

            String typeLabel = fSiege ? "🏰 Siege" : "🎲 Dice";
            String wallLabel = (hasWall && dist == 1 && !fSiege) ? " [🧱 Wall +2 def]" : "";
            String label     = String.format("⚔️ Attack! [%s] dist:%d%s", typeLabel, dist, wallLabel);

            String disabledReason;
            if (attackers.isEmpty())    disabledReason = "No military units on source hex";
            else if (!hasReadyAttacker) disabledReason = "All attackers out of AP";
            else if (!hasValidForDist)  disabledReason = "No Archer for range-2 attack";
            else                        disabledReason = "Ready";

            if (nm != null) {
                // ── Multiplayer mode: send AttackRequest to server ────────────
                // The server validates ownership, diplomatic status, AP, and range,
                // then executes the attack and broadcasts the updated state.
                final int srcQ = sourceHex.getQ(), srcR = sourceHex.getR();
                final int tgtQ = targetHex.getQ(), tgtR = targetHex.getR();
                actions.add(new MenuAction(label, canAttack, disabledReason, () ->
                        nm.sendRequest(gson.toJson(new AttackRequest(srcQ, srcR, tgtQ, tgtR)))));
            } else {
                // ── Single-player mode: execute attack locally (original behavior) ──
                actions.add(new MenuAction(label, canAttack, disabledReason, () -> {
                    CombatController cc = new CombatController(map);
                    cc.executeAttack(attackers, sourceHex, targetHex, fSiege, fAnimal, hasWall);
                    map.removeDeadUnits();
                    map.updateFogOfWar();
                }));
            }

            // "Attack Wall" sub-action (single-player only for now)
            if (hasWall && hasAnyEnemy && dist == 1 && nm == null) {
                actions.add(new MenuAction("⚔️ Attack Wall [🏰 Siege]", canAttack, disabledReason, () -> {
                    CombatController cc = new CombatController(map);
                    cc.executeAttack(attackers, sourceHex, targetHex, true, false, true);
                    map.removeDeadUnits();
                    map.updateFogOfWar();
                }));
            }
        }

        if (mc.isCapturable(selectedUnit, targetHex)) {
            boolean canCapture = !attackers.isEmpty() && attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
            actions.add(new MenuAction("🏴 Capture Hex (1 AP)", canCapture,
                    canCapture ? "No defenders — seize this hex" : "Need military unit with AP ≥ 1",
                    () -> {
                        attackers.stream().filter(u -> u.getCurrentAP() >= 1).findFirst()
                                .ifPresent(u -> u.consumeAP(1));
                        targetHex.setInsideBorder(true);
                        targetHex.setExplored(true);
                        map.updateFogOfWar();
                        GameEventDispatcher.fireBorderExpanded(targetHex.getQ(), targetHex.getR());
                    }));
        }
        return actions;
    }

    private static int getAttackDirection(GameMap map, Hex source, Hex target) {
        for (int i = 0; i < 6; i++) {
            if (map.getNeighbor(source, i) == target) return i;
        }
        return -1;
    }

    private static String getDestroyDisabledReason(BuildingType type, Builder builder) {
        if (type == BuildingType.TOWN_HALL)    return "Cannot destroy Town Hall";
        if (type == BuildingType.TRIBE_CAMP)   return "Cannot destroy Tribe Camp";
        if (type == BuildingType.TRADING_POST) return "Cannot destroy Trading Post";
        if (builder.getCurrentAP() < 1)        return "Not enough AP (need 1)";
        return "Builder must be on or adjacent to the hex";
    }
}