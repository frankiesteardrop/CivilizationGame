package controller;

import model.*;

/**
 * کنترلر مدیریت آپگریدها، تکنولوژی‌ها و تولید یونیت در Town Hall.
 * * [اصلاح حیاتی گام ۸]:
 * - بررسی Unit Cap حالا با احتساب یونیت‌هایی که در صف تولید (Queue) هستند انجام می‌شود
 * تا بازیکن نتواند از باگ Overpopulation سوءاستفاده کند.
 */
public class UpgradeController {

    // =========================================================
    // ثابت‌های اقتصادی بازی (Centralized Game Balance Values)
    // =========================================================

    // هزینه‌های ارتقای انبار
    public static final int WAREHOUSE_WOOD_COST = 100;
    public static final int WAREHOUSE_STONE_COST = 50;

    // هزینه‌های تکنولوژی‌ها
    public static final int TECH_STONE_MINE_WOOD = 50;

    public static final int TECH_IRON_MINE_WOOD = 100;
    public static final int TECH_IRON_MINE_STONE = 50;

    public static final int TECH_PROF_TOOLS_WOOD = 100;
    public static final int TECH_PROF_TOOLS_STONE = 100;
    public static final int TECH_PROF_TOOLS_IRON = 50;

    public static final int TECH_SETTLEMENT_WOOD = 150;
    public static final int TECH_SETTLEMENT_STONE = 100;
    public static final int TECH_SETTLEMENT_IRON = 50;

    // هزینه‌های ساخت و تولید یونیت‌ها
    public static final int WORKER_FOOD_COST = 20;
    public static final int WORKER_TURN_COST = 1;

    public static final int BUILDER_FOOD_COST = 30;
    public static final int BUILDER_WOOD_COST = 10;
    public static final int BUILDER_TURN_COST = 2;

    public static final int EXPLORER_FOOD_COST = 40;
    public static final int EXPLORER_WOOD_COST = 5;
    public static final int EXPLORER_TURN_COST = 3;

    public static final int BORDER_EXPANDER_FOOD_COST = 30;
    public static final int BORDER_EXPANDER_WOOD_COST = 20;
    public static final int BORDER_EXPANDER_STONE_COST = 10;
    public static final int BORDER_EXPANDER_TURN_COST = 3;

    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getWarehouseUpgradeLevel() >= 2) return false;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD,  WAREHOUSE_WOOD_COST)
                && inv.hasEnough(ResourceType.STONE, WAREHOUSE_STONE_COST);
    }

    public boolean canUnlockTech(String techType) {
        TownHall th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                return !th.isStoneMineUnlocked() && inv.hasEnough(ResourceType.WOOD, TECH_STONE_MINE_WOOD);
            case "IRON_MINE":
                return th.isStoneMineUnlocked() && !th.isIronMineUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  TECH_IRON_MINE_WOOD)
                        && inv.hasEnough(ResourceType.STONE, TECH_IRON_MINE_STONE);
            case "PROF_TOOLS":
                return th.isStoneMineUnlocked() && th.isIronMineUnlocked()
                        && !th.isProfessionalToolsUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  TECH_PROF_TOOLS_WOOD)
                        && inv.hasEnough(ResourceType.STONE, TECH_PROF_TOOLS_STONE)
                        && inv.hasEnough(ResourceType.IRON,  TECH_PROF_TOOLS_IRON);
            case "SETTLEMENT":
                return th.isIronMineUnlocked() && !th.isSettlementUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  TECH_SETTLEMENT_WOOD)
                        && inv.hasEnough(ResourceType.STONE, TECH_SETTLEMENT_STONE)
                        && inv.hasEnough(ResourceType.IRON,  TECH_SETTLEMENT_IRON);
            default:
                return false;
        }
    }

    public boolean canTrainUnit(String unitType) {
        TownHall th = gameMap.getTownHall();

        int futurePopulation = gameMap.getAliveUnitsCount() + (th.isProductionQueueEmpty() ? 0 : 1);
        if (futurePopulation >= gameMap.getUnitCap()) return false;

        if (!th.isProductionQueueEmpty()) return false;

        Inventory inv = th.getInventory();

        switch (unitType) {
            case "WORKER":
                return inv.hasEnough(ResourceType.FOOD, WORKER_FOOD_COST);
            case "BUILDER":
                return inv.hasEnough(ResourceType.FOOD, BUILDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, BUILDER_WOOD_COST);
            case "EXPLORER":
                return inv.hasEnough(ResourceType.FOOD, EXPLORER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, EXPLORER_WOOD_COST);
            case "BORDER_EXPANDER":
                return inv.hasEnough(ResourceType.FOOD,  BORDER_EXPANDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD,  BORDER_EXPANDER_WOOD_COST) && inv.hasEnough(ResourceType.STONE, BORDER_EXPANDER_STONE_COST);
            default:
                return false;
        }
    }

    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        inv.consumeResource(ResourceType.WOOD,  WAREHOUSE_WOOD_COST);
        inv.consumeResource(ResourceType.STONE, WAREHOUSE_STONE_COST);
        th.upgradeWarehouse();
    }

    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                inv.consumeResource(ResourceType.WOOD, TECH_STONE_MINE_WOOD);
                th.setStoneMineUnlocked(true);
                break;
            case "IRON_MINE":
                inv.consumeResource(ResourceType.WOOD,  TECH_IRON_MINE_WOOD);
                inv.consumeResource(ResourceType.STONE, TECH_IRON_MINE_STONE);
                th.setIronMineUnlocked(true);
                break;
            case "PROF_TOOLS":
                inv.consumeResource(ResourceType.WOOD,  TECH_PROF_TOOLS_WOOD);
                inv.consumeResource(ResourceType.STONE, TECH_PROF_TOOLS_STONE);
                inv.consumeResource(ResourceType.IRON,  TECH_PROF_TOOLS_IRON);
                th.setProfessionalToolsUnlocked(true);
                break;
            case "SETTLEMENT":
                inv.consumeResource(ResourceType.WOOD,  TECH_SETTLEMENT_WOOD);
                inv.consumeResource(ResourceType.STONE, TECH_SETTLEMENT_STONE);
                inv.consumeResource(ResourceType.IRON,  TECH_SETTLEMENT_IRON);
                th.setSettlementUnlocked(true);
                break;
        }
    }

    public void trainUnit(String unitType) {
        if (!canTrainUnit(unitType)) return;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        int q = th.getQ();
        int r = th.getR();

        switch (unitType) {
            case "WORKER": {
                boolean queued = th.queueProduction("Worker", WORKER_TURN_COST, () -> gameMap.getUnits().add(new Worker(q, r)));
                if (queued) inv.consumeResource(ResourceType.FOOD, WORKER_FOOD_COST);
                break;
            }
            case "BUILDER": {
                boolean queued = th.queueProduction("Builder", BUILDER_TURN_COST, () -> gameMap.getUnits().add(new Builder(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD, BUILDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, BUILDER_WOOD_COST);
                }
                break;
            }
            case "EXPLORER": {
                boolean queued = th.queueProduction("Explorer", EXPLORER_TURN_COST, () -> gameMap.getUnits().add(new Explorer(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD, EXPLORER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, EXPLORER_WOOD_COST);
                }
                break;
            }
            case "BORDER_EXPANDER": {
                boolean queued = th.queueProduction("Border Expander", BORDER_EXPANDER_TURN_COST, () -> gameMap.getUnits().add(new BorderExpander(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD,  BORDER_EXPANDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD,  BORDER_EXPANDER_WOOD_COST);
                    inv.consumeResource(ResourceType.STONE, BORDER_EXPANDER_STONE_COST);
                }
                break;
            }
        }
    }
}