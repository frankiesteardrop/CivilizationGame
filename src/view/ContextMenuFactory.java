package view;

import com.google.gson.Gson;
import controller.CombatController;
import controller.MainController;
import controller.MenuAction;
import model.*;
import network.client.NetworkManager;
import network.messages.game.AttackRequest;
import network.messages.game.CancelProductionRequest;
import network.messages.game.CraftItemRequest;
import network.messages.game.ItemUseRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ContextMenuFactory {

    private static List<MenuAction> notYourTurnMenu() {
        return List.of(
                new MenuAction(
                        "⏳ Wait for your turn!",
                        false,
                        "Actions are locked until it is your turn.",
                        null
                )
        );
    }

    public static List<MenuAction> buildTownHallMenu(
            MainController mc) {

        if (mc.getNetworkManager() != null
                && !mc.isMyTurn()) {

            return notYourTurnMenu();
        }

        List<MenuAction> actions =
                new ArrayList<>();

        GameMap map =
                mc.getGameMap();

        TownHall th =
                map.getPlayerTownHall(mc.getMyPlayerId());

        if (th == null) {
            return List.of(new MenuAction("⚠️ No Town Hall in this Empire", false, null));
        }

        NetworkManager nm =
                mc.getNetworkManager();

        boolean qEmpty =
                th.isProductionQueueEmpty();

        String prefix =
                qEmpty
                        ? ""
                        : "⏳ [BUSY] ";

        boolean isMilCap =
                map.getMilitaryUnitCount()
                        >= map.getMilitaryUnitCap();

        String milPrefix =
                isMilCap
                        ? "⚔️ [CAP] "
                        : prefix;

        if (!qEmpty) {
            actions.add(
                    new MenuAction(
                            "🚫 Cancel Current Production (No Refund)",
                            true,
                            () -> {
                                if (nm != null) {
                                    nm.sendRequest(
                                            new Gson().toJson(
                                                    new CancelProductionRequest()
                                            )
                                    );
                                } else {
                                    th.cancelCurrentProduction();
                                }
                            }
                    ).setConfirmation(
                            "Are you sure you want to cancel the current production?\n\n"
                                    + "⚠️ ALL INVESTED RESOURCES WILL BE LOST!"
                    )
            );
        }

        String whLabel =
                th.getLevel() >= 3
                        ? "✅ Capital MAXED"
                        : String.format(
                        prefix
                                + "📦 Upgrade TH → Level %d",
                        th.getLevel() + 1
                );

        actions.add(new MenuAction(whLabel, mc.getUpgradeController().canAffordWarehouseUpgrade(), () -> mc.getUpgradeController().handleWarehouseUpgrade(nm)));
        actions.add(new MenuAction(th.isStoneMineUnlocked() ? "✅ ⛏️ Tech: Stone Mine" : String.format(prefix + "⛏️ Stone Mine (%dW)", GameConfig.TECH_STONE_MINE_WOOD), mc.getUpgradeController().canUnlockTech("STONE_MINE"), () -> mc.getUpgradeController().unlockTech("STONE_MINE", nm)));
        actions.add(new MenuAction(th.isIronMineUnlocked() ? "✅ 🔩 Tech: Iron Mine" : String.format(prefix + "🔩 Iron Mine (%dW, %dS)", GameConfig.TECH_IRON_MINE_WOOD, GameConfig.TECH_IRON_MINE_STONE), mc.getUpgradeController().canUnlockTech("IRON_MINE"), () -> mc.getUpgradeController().unlockTech("IRON_MINE", nm)));
        actions.add(new MenuAction(th.isProfessionalToolsUnlocked() ? "✅ 🔧 Tech: Steel Tools" : String.format(prefix + "🔧 Steel Tools (%dI) [Requires Iron Mine tech]", GameConfig.TECH_STEEL_TOOLS_IRON), mc.getUpgradeController().canUnlockTech("PROF_TOOLS"), "Requires TH Lv2 + Iron Mine tech + " + GameConfig.TECH_STEEL_TOOLS_IRON + " Iron", () -> mc.getUpgradeController().unlockTech("PROF_TOOLS", nm)));
        actions.add(new MenuAction(th.isSeafaringUnlocked() ? "✅ ⛵ Tech: Seafaring" : String.format(prefix + "⛵ Seafaring (%dW)", GameConfig.TECH_SEAFARING_WOOD), mc.getUpgradeController().canUnlockTech("SEAFARING"), () -> mc.getUpgradeController().unlockTech("SEAFARING", nm)));
        actions.add(new MenuAction(th.isDefensiveArchUnlocked() ? "✅ 🏰 Tech: Defensive Arch" : String.format(prefix + "🏰 Defensive Arch (%dS)", GameConfig.TECH_DEFENSIVE_ARCH_STONE), mc.getUpgradeController().canUnlockTech("DEFENSIVE_ARCH"), () -> mc.getUpgradeController().unlockTech("DEFENSIVE_ARCH", nm)));
        actions.add(new MenuAction(String.format(prefix + "👷 Worker (%dF)", GameConfig.WORKER_FOOD_COST), mc.getUpgradeController().canTrainUnit("WORKER"), () -> mc.getUpgradeController().trainUnit("WORKER", nm)));
        actions.add(new MenuAction(String.format(prefix + "🔨 Builder (%dF, %dW)", GameConfig.BUILDER_FOOD_COST, GameConfig.BUILDER_WOOD_COST), mc.getUpgradeController().canTrainUnit("BUILDER"), () -> mc.getUpgradeController().trainUnit("BUILDER", nm)));
        actions.add(new MenuAction(String.format(prefix + "🧭 Explorer (%dF, %dW)", GameConfig.EXPLORER_FOOD_COST, GameConfig.EXPLORER_WOOD_COST), mc.getUpgradeController().canTrainUnit("EXPLORER"), () -> mc.getUpgradeController().trainUnit("EXPLORER", nm)));
        actions.add(new MenuAction(milPrefix + "⚔️ Swordsman (20F, 10W)", mc.getUpgradeController().canTrainUnit("SWORDSMAN"), () -> mc.getUpgradeController().trainUnit("SWORDSMAN", nm)));
        actions.add(new MenuAction(milPrefix + "🏹 Archer (20F, 20W) [TH L2]", mc.getUpgradeController().canTrainUnit("ARCHER"), () -> mc.getUpgradeController().trainUnit("ARCHER", nm)));
        actions.add(new MenuAction(milPrefix + "💣 Catapult (30W, 20S, 10I) [TH L2]", mc.getUpgradeController().canTrainUnit("CATAPULT"), "Requires TH Level 2 + 30 Wood + 20 Stone + 10 Iron", () -> mc.getUpgradeController().trainUnit("CATAPULT", nm)));

        return actions;
    }

    public static List<MenuAction> buildStableMenu(MainController mc) {
        if (mc.getNetworkManager() != null && !mc.isMyTurn()) return notYourTurnMenu();

        GameMap map = mc.getGameMap();
        TownHall th = map.getPlayerTownHall(mc.getMyPlayerId());
        if (th == null) return new ArrayList<>();

        boolean qEmpty   = th.isProductionQueueEmpty();
        boolean isMilCap = map.getMilitaryUnitCount() >= map.getMilitaryUnitCap();
        String prefix    = (isMilCap ? "⚔️ [CAP] " : "") + (!qEmpty ? "⏳ [BUSY] " : "");

        return List.of(
                new MenuAction(
                        prefix + "🏇 Train Cavalry (30F, 20I) [TH L2 + Stable]",
                        mc.getUpgradeController().canTrainUnit("CAVALRY"),
                        "Requires TH Level 2 + active Stable + unit cap not full + resources",
                        () -> mc.getUpgradeController().trainUnit("CAVALRY", mc.getNetworkManager())
                )
        );
    }

    public static List<MenuAction> buildBazaarMenu(MainController mc, Bazaar bazaar, Runnable onTradeAction) {
        if (mc.getNetworkManager() != null && !mc.isMyTurn()) return notYourTurnMenu();

        List<MenuAction> actions = new ArrayList<>();
        boolean traded = bazaar.hasTraded();
        int level      = bazaar.getLevel();

        actions.add(new MenuAction("⚖️ Trade (Level " + level + ")", !traded, "Already traded this turn", onTradeAction));

        if (bazaar.canUpgrade()) {
            int stoneCost = (level == 1) ? 30 : 60;
            boolean canUpg = mc.getTradeController().canUpgradeBazaar(bazaar);
            actions.add(new MenuAction("⬆️ Upgrade Bazaar → Level " + (level + 1) + " (" + stoneCost + " Stone)", canUpg, "Need " + stoneCost + " Stone to upgrade", () -> mc.getTradeController().upgradeBazaar(bazaar)));
        } else {
            actions.add(new MenuAction("✅ Bazaar is at Max Level (3)", false, null));
        }

        return actions;
    }

    public static List<MenuAction> buildTradingPostMenu(MainController mc, TradingPost post, Hex hex, Runnable onTradeAction) {
        if (mc.getNetworkManager() != null && !mc.isMyTurn()) return notYourTurnMenu();

        List<MenuAction> actions = new ArrayList<>();

        if (!hex.isInsideBorder()) {
            actions.add(new MenuAction("⛔ Hex must be in your territory to trade", false, null));
            return actions;
        }

        boolean traded = post.hasTraded();
        actions.add(new MenuAction("🏪 Trade (80% Rate - Custom Amount)", !traded, "Already traded this turn", onTradeAction));
        return actions;
    }

    public static List<MenuAction> buildApothecaryMenu(MainController mc, Apothecary apothecary, Hex hex) {
        if (mc.getNetworkManager() != null && !mc.isMyTurn()) return notYourTurnMenu();

        List<MenuAction> actions = new ArrayList<>();

        if (!hex.isInsideBorder()) {
            actions.add(new MenuAction("⛔ Must be inside your territory to use", false, null));
            return actions;
        }
        if (apothecary.isDestroyed()) {
            actions.add(new MenuAction("⚠️ This Apothecary is destroyed", false, null));
            return actions;
        }

        String currentlyCrafting = apothecary.getCurrentlyCrafting();
        if (currentlyCrafting != null) {
            actions.add(new MenuAction("⏳ Currently crafting: " + currentlyCrafting + " (1 turn remaining)", false, null));
            return actions;
        }

        Inventory inv = mc.getGameMap().getPlayerInventory(mc.getMyPlayerId());
        if (inv == null) return actions;

        Gson gson = new Gson();

        for (Apothecary.ItemType itemType : Apothecary.ItemType.values()) {
            boolean canCraft = inv.hasEnough(ResourceType.FOOD,  itemType.getFoodCost())
                    && inv.hasEnough(ResourceType.STONE, itemType.getStoneCost())
                    && inv.hasEnough(ResourceType.IRON,  itemType.getIronCost())
                    && inv.hasEnough(ResourceType.WOOD,  itemType.getWoodCost());

            StringBuilder costLabel = new StringBuilder();
            if (itemType.getFoodCost()  > 0) costLabel.append(itemType.getFoodCost()).append("F ");
            if (itemType.getStoneCost() > 0) costLabel.append(itemType.getStoneCost()).append("S ");
            if (itemType.getIronCost()  > 0) costLabel.append(itemType.getIronCost()).append("I ");
            if (itemType.getWoodCost()  > 0) costLabel.append(itemType.getWoodCost()).append("W ");

            String label          = String.format("⚗️ Craft %s (%s)", itemType.getDisplayName(), costLabel.toString().trim());
            String disabledReason = "Insufficient resources: need " + itemType.getFoodCost() + "F " + itemType.getStoneCost() + "S " + itemType.getIronCost() + "I " + itemType.getWoodCost() + "W";
            final Apothecary.ItemType finalItemType = itemType;

            actions.add(new MenuAction(label, canCraft, disabledReason, () -> {
                NetworkManager nm = mc.getNetworkManager();
                if (nm != null) {
                    nm.sendRequest(gson.toJson(new CraftItemRequest(hex.getQ(), hex.getR(), finalItemType.name())));
                } else {
                    inv.consumeResource(ResourceType.FOOD,  finalItemType.getFoodCost());
                    inv.consumeResource(ResourceType.STONE, finalItemType.getStoneCost());
                    inv.consumeResource(ResourceType.IRON,  finalItemType.getIronCost());
                    inv.consumeResource(ResourceType.WOOD,  finalItemType.getWoodCost());
                    apothecary.queueItem(finalItemType.name());
                    GameEventDispatcher.fireNotification("⚗️ Crafting " + finalItemType.getDisplayName() + " — ready at end of turn!");
                    GameEventDispatcher.fireBuildingConstructed(hex);
                }
            }));
        }

        actions.add(new MenuAction("─────────────────", false, null));

        java.util.Map<String, Integer> currentItems = inv.getItems();
        if (currentItems.isEmpty()) {
            actions.add(new MenuAction("📦 Inventory: empty", false, null));
        } else {
            currentItems.forEach((name, qty) -> {
                if (qty > 0) actions.add(new MenuAction("📦 " + name + " × " + qty, false, null));
            });
        }

        return actions;
    }

    public static List<MenuAction> buildUnitMenu(MainController mc, Unit selectedUnit, Hex targetHex) {
        if (mc.getNetworkManager() != null && !mc.isMyTurn()) return notYourTurnMenu();

        List<MenuAction> actions = new ArrayList<>();
        NetworkManager nm        = mc.getNetworkManager();
        boolean isSameHex        = selectedUnit.getQ() == targetHex.getQ() && selectedUnit.getR() == targetHex.getR();
        Inventory inv            = mc.getGameMap().getPlayerInventory(selectedUnit.getOwnerId());
        if (inv == null) return actions;

        Gson gson = new Gson();

        if (!isSameHex) {

            if (selectedUnit.getAttackRange() > 0) {
                actions.addAll(buildAttackMenu(mc, selectedUnit, targetHex));
            }

            if (inv.hasItem("TELEPORT")) {
                boolean isValidDest = targetHex.isExplored()
                        && targetHex.getTerrainType() != TerrainType.SEA
                        && targetHex.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                        && !mc.getGameMap().hasUnitAt(targetHex.getQ(), targetHex.getR());
                boolean canUse  = !selectedUnit.hasUsedItemThisTurn() && isValidDest;
                String reason   = selectedUnit.hasUsedItemThisTurn() ? "Already used an item this turn" : "Invalid destination";

                actions.add(new MenuAction("✨ Teleport Here (Free Action)", canUse, canUse ? null : reason, () -> {
                    if (nm != null) {
                        nm.sendRequest(gson.toJson(new ItemUseRequest(selectedUnit.getId(), selectedUnit.getQ(), selectedUnit.getR(), "TELEPORT", targetHex.getQ(), targetHex.getR())));
                    } else {
                        inv.consumeItem("TELEPORT");
                        selectedUnit.setUsedItemThisTurn(true);
                        selectedUnit.moveTo(targetHex.getQ(), targetHex.getR(), 0);
                        mc.getGameMap().updateFogOfWar();
                    }
                }));
            }

            if (mc.isCapturable(selectedUnit, targetHex)) {
                List<Unit> attackers = mc.getGameMap().getUnits().stream()
                        .filter(u -> u.isAlive() && u.getQ() == selectedUnit.getQ() && u.getR() == selectedUnit.getR() && isCaptureUnit(u))
                        .collect(Collectors.toList());

                boolean canCapture   = !attackers.isEmpty() && attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
                final Unit captureUnit = attackers.stream().filter(u -> u.getCurrentAP() >= 1).findFirst().orElse(null);

                actions.add(new MenuAction("🏴 Capture Hex (1 AP)", canCapture,
                        canCapture ? "No defenders — seize this hex" : "Need military unit with AP ≥ 1",
                        () -> { if (captureUnit != null) mc.requestCaptureHex(captureUnit, targetHex); }));
            }

        } else {

            if (inv.hasItem("MOBILITY")) {
                boolean canUse = !selectedUnit.hasUsedItemThisTurn();
                actions.add(new MenuAction("⚡ Use Mobility Potion (+2 AP)", canUse, canUse ? null : "Already used an item this turn", () -> {
                    if (nm != null) {
                        nm.sendRequest(gson.toJson(new ItemUseRequest(selectedUnit.getId(), selectedUnit.getQ(), selectedUnit.getR(), "MOBILITY", 0, 0)));
                    } else {
                        inv.consumeItem("MOBILITY");
                        selectedUnit.setUsedItemThisTurn(true);
                        selectedUnit.addTemporaryAP(2);
                        GameEventDispatcher.fireUnitStateChanged(selectedUnit);
                    }
                }));
            }

            if (inv.hasItem("COMBAT")) {
                boolean canUse = !selectedUnit.hasUsedItemThisTurn();
                actions.add(new MenuAction("⚔️ Use Combat Potion (+1 Dice, +5 Siege)", canUse, canUse ? null : "Already used an item this turn", () -> {
                    if (nm != null) {
                        nm.sendRequest(gson.toJson(new ItemUseRequest(selectedUnit.getId(), selectedUnit.getQ(), selectedUnit.getR(), "COMBAT", 0, 0)));
                    } else {
                        inv.consumeItem("COMBAT");
                        selectedUnit.setUsedItemThisTurn(true);
                        selectedUnit.setTemporaryCombatDiceBonus(1);
                        selectedUnit.setTemporarySiegeBonus(5);
                        GameEventDispatcher.fireUnitStateChanged(selectedUnit);
                    }
                }));
            }

            if (selectedUnit.getType() == UnitType.BUILDER) {
                Builder builder  = (Builder) selectedUnit;
                Building existing = targetHex.getBuilding();

                if (existing != null && !existing.isDestroyed()) {
                    boolean canDestroy = mc.getBuildController().canDestroy(targetHex, "BUILDING", 0, builder);
                    BuildingType bType = existing.getType();
                    actions.add(new MenuAction("🗑️ Destroy " + bType.name() + " (-1 AP, no refund)", canDestroy,
                            getDestroyDisabledReason(bType, builder),
                            () -> mc.getBuildController().destroyStructure(builder, targetHex, "BUILDING", 0, nm))
                            .setConfirmation("Destroy " + bType.name() + "?\n\n⚠️ No resources will be refunded.\nWorkers inside will be relocated."));

                } else if (!targetHex.isInsideBorder()) {
                    actions.add(new MenuAction("⛔ Must be inside your borders", false, null));
                } else {
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.TOWN_HALL,   "🏰 Town Hall (200W, 200S, 100I)", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.LUMBER_MILL, "🌲 Lumber Mill", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.FARM,        "🌾 Farm", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.STABLE,      "🐄 Stable", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.STONE_MINE,  "⛏️ Stone Mine", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.IRON_MINE,   "🔩 Iron Mine", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.SETTLEMENT,  "🏘️ Settlement (⚠️ -1 Happiness)", nm));

                    TownHall empireTH = mc.getGameMap().getPlayerTownHall(mc.getMyPlayerId());
                    boolean hasDockDiscount = (empireTH != null && empireTH.getDiscountedDocks() > 0);
                    String dockLabel = hasDockDiscount ? "⚓ Dock [🎉 FREE by Mission!]" : "⚓ Dock [TH L2]";
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.DOCK,        dockLabel, nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.MONUMENT,    "🏛️ Monument", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.BAZAAR,      "⚖️ Bazaar [TH L2]", nm));
                    actions.add(createBuildAction(mc, builder, targetHex, BuildingType.APOTHECARY,  "⚗️ Apothecary [TH L2, Plains]", nm));
                }

            } else if (selectedUnit.getType() == UnitType.WORKER) {
                Worker worker = (Worker) selectedUnit;

                if (worker.isStationed()) {
                    actions.add(new MenuAction("🚪 Leave Facility", mc.getUnitController().canEject(worker),
                            () -> mc.getUnitController().handleEject(worker, nm)));
                } else {
                    Building b = targetHex.getBuilding();
                    if (b != null && !b.isDestroyed()
                            && b.getType() != BuildingType.TOWN_HALL
                            && b.getType() != BuildingType.MONUMENT
                            && b.getMaxWorkers() > 0) {
                        boolean can = mc.getUnitController().canStation(worker, targetHex, mc.getGameMap());
                        actions.add(new MenuAction("⚙️ Station in " + b.getType().name(), can,
                                () -> mc.getUnitController().handleStation(worker, targetHex, mc.getGameMap(), nm)));
                    } else {
                        actions.add(new MenuAction("⛔ No workable facility here", false, null));
                    }
                }
            }
        }

        return actions;
    }

    private static boolean isCaptureUnit(Unit unit) {
        if (unit == null) return false;
        return switch (unit.getType()) {
            case SWORDSMAN, ARCHER, CAVALRY -> true;
            default -> false;
        };
    }

    private static MenuAction createBuildAction(MainController mc, Builder builder, Hex hex, BuildingType type, String label, NetworkManager nm) {
        boolean canBuild = mc.getBuildController().canBuild(type, hex, builder);
        return new MenuAction(label + " (-" + type.getApCost() + "AP)", canBuild, () -> {
            mc.getBuildController().buildStructure(builder, type, hex, nm);
            GameEventDispatcher.fireUnitStateChanged(builder);
        });
    }

    private static List<MenuAction> buildAttackMenu(MainController mc, Unit selectedUnit, Hex targetHex) {
        List<MenuAction> actions = new ArrayList<>();
        GameMap map   = mc.getGameMap();
        Hex sourceHex = map.getHexAt(selectedUnit.getQ(), selectedUnit.getR());
        if (sourceHex == null) return actions;

        int dist = map.getHexDistance(selectedUnit.getQ(), selectedUnit.getR(), targetHex.getQ(), targetHex.getR());
        if (dist < 1 || dist > 2) {
            actions.add(new MenuAction("⛔ Target out of range (max 2)", false, null));
            return actions;
        }

        List<Unit> attackers = map.getUnits().stream()
                .filter(u -> u.isAlive() && u.getQ() == selectedUnit.getQ() && u.getR() == selectedUnit.getR() && isMilitaryUnit(u))
                .collect(Collectors.toList());

        boolean hasAnyEnemy = mc.isAttackable(targetHex);

        boolean tempHasWall = false;
        if (dist == 1) {
            int dir = getAttackDirection(map, sourceHex, targetHex);
            if (dir >= 0) tempHasWall = sourceHex.hasWall(dir);
        }
        final boolean hasWall  = tempHasWall;
        final NetworkManager nm = mc.getNetworkManager();
        final Gson gson         = new Gson();

        if (hasAnyEnemy) {
            boolean hasReadyAttacker = attackers.stream().anyMatch(u -> u.getCurrentAP() >= 1);
            boolean hasValidForDist  = attackers.stream().anyMatch(u -> isValidAttackerForDistance(u, dist));
            boolean canAttack        = !attackers.isEmpty() && hasReadyAttacker && hasValidForDist;

            final boolean fAnimal = map.getUnits().stream().anyMatch(u -> u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR() && u.getType() == UnitType.BEAR);

            final boolean fEnemyUnit;
            String myId = mc.getMyPlayerId();
            if (nm != null && myId != null) {
                fEnemyUnit = map.getUnits().stream().anyMatch(u ->
                        u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                                && !myId.equals(u.getOwnerId()) && u.getType() != UnitType.BEAR);
            } else {
                fEnemyUnit = map.getUnits().stream().anyMatch(u ->
                        u.isAlive() && u.getQ() == targetHex.getQ() && u.getR() == targetHex.getR()
                                && u.isEnemy());
            }

            final boolean fSiege     = !(fAnimal || fEnemyUnit);

            String typeLabel     = fSiege ? "🏰 Siege" : "🎲 Dice";
            String wallLabel     = (hasWall && dist == 1 && !fSiege) ? " [🧱 Wall +2 def]" : "";
            String label         = String.format("⚔️ Attack! [%s] dist:%d%s", typeLabel, dist, wallLabel);

            String disabledReason;
            if (attackers.isEmpty())          disabledReason = "No military units on source hex";
            else if (!hasReadyAttacker)        disabledReason = "All eligible attackers are out of AP";
            else if (!hasValidForDist)         disabledReason = dist == 2 ? "Need an Archer or Catapult for range-2 attack" : "No suitable military attacker";
            else                               disabledReason = "Ready";

            if (nm != null) {
                final int tgtQ = targetHex.getQ();
                final int tgtR = targetHex.getR();

                final List<String> finalAttackerIds = attackers.stream()
                        .map(Unit::getId)
                        .collect(Collectors.toList());

                actions.add(new MenuAction(label, canAttack, disabledReason,
                        () -> nm.sendRequest(gson.toJson(new AttackRequest(finalAttackerIds, tgtQ, tgtR)))));

            } else {
                actions.add(new MenuAction(label, canAttack, disabledReason, () -> {
                    CombatController cc = new CombatController(map);
                    cc.executeAttack(attackers, sourceHex, targetHex, fSiege, fAnimal, hasWall);
                    map.removeDeadUnits();
                    map.updateFogOfWar();
                }));
            }

            if (hasWall && hasAnyEnemy && dist == 1 && nm == null) {
                actions.add(new MenuAction("⚔️ Attack Wall [🏰 Siege]", canAttack, disabledReason, () -> {
                    CombatController cc = new CombatController(map);
                    cc.executeAttack(attackers, sourceHex, targetHex, true, false, true);
                    map.removeDeadUnits();
                    map.updateFogOfWar();
                }));
            }
        }

        return actions;
    }

    private static boolean isMilitaryUnit(Unit unit) {
        if (unit == null) return false;
        return switch (unit.getType()) {
            case SWORDSMAN, ARCHER, CAVALRY, CATAPULT -> true;
            default -> false;
        };
    }

    private static boolean isValidAttackerForDistance(Unit unit, int distance) {
        if (!isMilitaryUnit(unit)) return false;
        if (unit.getAttackRange() < distance) return false;
        if (distance == 2) return unit.getType() == UnitType.ARCHER || unit.getType() == UnitType.CATAPULT;
        return distance == 1;
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