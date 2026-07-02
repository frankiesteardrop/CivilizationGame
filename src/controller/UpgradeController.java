package controller;

import model.*;

/**
 * کنترلر مدیریت آپگریدها، تکنولوژی‌ها و تولید یونیت در Town Hall.
 *
 * [گام ۱ - اصلاح]: آپگرید انبار و آنلاک تکنولوژی‌ها دیگر Instant نیستند.
 * طبق داک (بخش HUD): «صف تولید Town Hall (یونیت یا آپگرید در حال ساخت)» —
 * پس این دو دسته هم مانند تولید یونیت باید از صف تک‌آیتمی TownHall
 * (queueProduction) عبور کنند و چند نوبت طول بکشند. الگوی دقیقاً مشابه
 * trainUnit() اینجا برای handleWarehouseUpgrade() و unlockTech() تکرار
 * شده است: هزینه فقط زمانی که آیتم با موفقیت وارد صف شود کسر می‌شود،
 * و اثر واقعی (upgradeWarehouse / setXUnlocked) در Runnable مربوطه،
 * پس از اتمام صف، اجرا می‌شود.
 */
public class UpgradeController {

    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getWarehouseUpgradeLevel() >= 2) return false;
        // [گام ۱ - اصلاح]: تا وقتی صف تولید خالی نباشد (چه یونیت، چه آپگرید دیگر)
        // نمی‌توان آپگرید جدیدی را صف کرد — دقیقاً هم‌راستا با canTrainUnit().
        if (!th.isProductionQueueEmpty()) return false;

        int woodCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_WOOD : GameConfig.WAREHOUSE_LVL2_WOOD;
        int stoneCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_STONE : GameConfig.WAREHOUSE_LVL2_STONE;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD, woodCost)
                && inv.hasEnough(ResourceType.STONE, stoneCost);
    }

    public boolean canUnlockTech(String techType) {
        TownHall th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        // [گام ۱ - اصلاح]: همان محدودیت صف تک‌آیتمی برای تمام تکنولوژی‌ها.
        if (!th.isProductionQueueEmpty()) return false;

        switch (techType) {
            case "STONE_MINE":
                return !th.isStoneMineUnlocked() && inv.hasEnough(ResourceType.WOOD, GameConfig.TECH_STONE_MINE_WOOD);
            case "IRON_MINE":
                return th.isStoneMineUnlocked() && !th.isIronMineUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  GameConfig.TECH_IRON_MINE_WOOD)
                        && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_IRON_MINE_STONE);
            case "PROF_TOOLS":
                return th.isStoneMineUnlocked() && th.isIronMineUnlocked()
                        && !th.isProfessionalToolsUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  GameConfig.TECH_PROF_TOOLS_WOOD)
                        && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_PROF_TOOLS_STONE)
                        && inv.hasEnough(ResourceType.IRON,  GameConfig.TECH_PROF_TOOLS_IRON);
            case "SETTLEMENT":
                return th.isIronMineUnlocked() && !th.isSettlementUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  GameConfig.TECH_SETTLEMENT_WOOD)
                        && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_SETTLEMENT_STONE)
                        && inv.hasEnough(ResourceType.IRON,  GameConfig.TECH_SETTLEMENT_IRON);
            default:
                return false;
        }
    }

    public boolean canTrainUnit(String unitType) {
        TownHall th = gameMap.getTownHall();

        if (!th.isProductionQueueEmpty()) return false;
        if (gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) return false;

        Inventory inv = th.getInventory();

        switch (unitType) {
            case "WORKER":
                return inv.hasEnough(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST);
            case "BUILDER":
                return inv.hasEnough(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST);
            case "EXPLORER":
                return inv.hasEnough(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST);
            case "BORDER_EXPANDER":
                return inv.hasEnough(ResourceType.FOOD,  GameConfig.BORDER_EXPANDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD,  GameConfig.BORDER_EXPANDER_WOOD_COST) && inv.hasEnough(ResourceType.STONE, GameConfig.BORDER_EXPANDER_STONE_COST);
            default:
                return false;
        }
    }

    /**
     * [گام ۱ - اصلاح]: آپگرید انبار اکنون Instant نیست.
     * هزینه (بر اساس سطح فعلی، پیش از صف‌شدن) محاسبه و فقط در صورت
     * پذیرفته‌شدن در صف کسر می‌شود. اثر واقعی (upgradeWarehouse) در
     * Runnable، پس از اتمام نوبت‌های لازم، توسط
     * TownHall.advanceProductionQueue() اجرا خواهد شد.
     */
    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        int woodCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_WOOD : GameConfig.WAREHOUSE_LVL2_WOOD;
        int stoneCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_STONE : GameConfig.WAREHOUSE_LVL2_STONE;

        if (th.queueProduction("Warehouse Upgrade", GameConfig.WAREHOUSE_UPGRADE_TURN_COST, th::upgradeWarehouse)) {
            inv.consumeResource(ResourceType.WOOD, woodCost);
            inv.consumeResource(ResourceType.STONE, stoneCost);
        }
    }

    /**
     * [گام ۱ - اصلاح]: آنلاک تکنولوژی‌ها اکنون Instant نیست.
     * برای هر تکنولوژی، هزینه فقط در صورت پذیرفته‌شدن در صف کسر می‌شود
     * و اثر واقعی (setXUnlocked(true)) در Runnable مربوطه، پس از اتمام
     * نوبت‌های لازم، اجرا می‌شود.
     */
    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                if (th.queueProduction("Tech: Stone Mine", GameConfig.TECH_STONE_MINE_TURN_COST,
                        () -> th.setStoneMineUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD, GameConfig.TECH_STONE_MINE_WOOD);
                }
                break;
            case "IRON_MINE":
                if (th.queueProduction("Tech: Iron Mine", GameConfig.TECH_IRON_MINE_TURN_COST,
                        () -> th.setIronMineUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_IRON_MINE_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_IRON_MINE_STONE);
                }
                break;
            case "PROF_TOOLS":
                if (th.queueProduction("Tech: Prof. Tools", GameConfig.TECH_PROF_TOOLS_TURN_COST,
                        () -> th.setProfessionalToolsUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_PROF_TOOLS_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_PROF_TOOLS_STONE);
                    inv.consumeResource(ResourceType.IRON,  GameConfig.TECH_PROF_TOOLS_IRON);
                }
                break;
            case "SETTLEMENT":
                if (th.queueProduction("Tech: Settlement", GameConfig.TECH_SETTLEMENT_TURN_COST,
                        () -> th.setSettlementUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_SETTLEMENT_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_SETTLEMENT_STONE);
                    inv.consumeResource(ResourceType.IRON,  GameConfig.TECH_SETTLEMENT_IRON);
                }
                break;
        }
    }

    public void trainUnit(String unitType) {
        if (!canTrainUnit(unitType)) return;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (unitType) {
            case "WORKER":
                if (th.queueProduction("Worker", GameConfig.WORKER_TURN_COST, () -> spawnSpecificUnit("WORKER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST);
                }
                break;
            case "BUILDER":
                if (th.queueProduction("Builder", GameConfig.BUILDER_TURN_COST, () -> spawnSpecificUnit("BUILDER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST);
                }
                break;
            case "EXPLORER":
                if (th.queueProduction("Explorer", GameConfig.EXPLORER_TURN_COST, () -> spawnSpecificUnit("EXPLORER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST);
                }
                break;
            case "BORDER_EXPANDER":
                if (th.queueProduction("Border Expander", GameConfig.BORDER_EXPANDER_TURN_COST, () -> spawnSpecificUnit("BORDER_EXPANDER"))) {
                    inv.consumeResource(ResourceType.FOOD,  GameConfig.BORDER_EXPANDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.BORDER_EXPANDER_WOOD_COST);
                    inv.consumeResource(ResourceType.STONE,