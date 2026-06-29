package controller;

import model.*;

/**
 * کنترلر مدیریت آپگریدها، تکنولوژی‌ها و تولید یونیت در Town Hall.
 *
 * ترتیب آپگریدها طبق داک:
 * ۱. ارتقا انبار ۱ (چوب + سنگ)
 * ۲. ارتقا انبار ۲ (چوب + سنگ بیشتر)
 * ۳. تکنولوژی معدن سنگ (چوب)
 * ۴. تکنولوژی معدن آهن (چوب + سنگ) — پس از معدن سنگ
 * ۵. تکنولوژی ابزار حرفه‌ای (چوب + سنگ + آهن) — پس از هر دو معدن
 * ۶. تکنولوژی ساخت شهرک (چوب + سنگ + آهن) — پس از معدن آهن
 */
public class UpgradeController {
    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    // =========================================================
    // بررسی امکان ارتقای انبار
    // =========================================================

    /**
     * بررسی امکان ارتقای انبار (حداکثر ۲ بار).
     * هزینه هر بار: ۱۰۰ چوب + ۵۰ سنگ
     */
    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getWarehouseUpgradeLevel() >= 2) return false;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD, 100)
                && inv.hasEnough(ResourceType.STONE, 50);
    }

    // =========================================================
    // بررسی امکان آنلاک تکنولوژی
    // =========================================================

    /**
     * بررسی امکان آنلاک تکنولوژی مشخص.
     *
     * ترتیب پیش‌نیازها دقیقاً طبق داک رعایت شده:
     * - IRON_MINE: نیاز به STONE_MINE
     * - PROF_TOOLS: نیاز به هر دو معدن
     * - SETTLEMENT: نیاز به معدن آهن (چون آهن می‌خواهد)
     */
    public boolean canUnlockTech(String techType) {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {

            case "STONE_MINE":
                return !th.isStoneMineUnlocked()
                        && inv.hasEnough(ResourceType.WOOD, 50);

            case "IRON_MINE":
                // پیش‌نیاز: تکنولوژی معدن سنگ باید آنلاک شده باشد
                return th.isStoneMineUnlocked()
                        && !th.isIronMineUnlocked()
                        && inv.hasEnough(ResourceType.WOOD, 100)
                        && inv.hasEnough(ResourceType.STONE, 50);

            case "PROF_TOOLS":
                // پیش‌نیاز: هر دو معدن باید آنلاک شده باشند
                return th.isStoneMineUnlocked()
                        && th.isIronMineUnlocked()
                        && !th.isProfessionalToolsUnlocked()
                        && inv.hasEnough(ResourceType.WOOD, 100)
                        && inv.hasEnough(ResourceType.STONE, 100)
                        && inv.hasEnough(ResourceType.IRON, 50);

            case "SETTLEMENT":
                // اصلاح گام ۳: پیش‌نیاز معدن آهن + هزینه آهن اضافه شد
                return th.isIronMineUnlocked()
                        && !th.isSettlementUnlocked()
                        && inv.hasEnough(ResourceType.WOOD, 150)
                        && inv.hasEnough(ResourceType.STONE, 100)
                        && inv.hasEnough(ResourceType.IRON, 50);

            default:
                return false;
        }
    }

    // =========================================================
    // بررسی امکان تولید یونیت
    // =========================================================

    /**
     * بررسی امکان تولید یونیت جدید.
     *
     * شرایط: Unit Cap رعایت شود + منابع کافی + صف تولید خالی باشد.
     * توجه: منابع هنگام شروع تولید کسر می‌شوند، نه هنگام اتمام.
     */
    public boolean canTrainUnit(String unitType) {
        // بررسی Unit Cap
        if (gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) return false;

        // بررسی اینکه صف تولید خالی باشد (از هم‌زمانی چند آیتم جلوگیری می‌کند)
        if (!gameMap.getTownHall().getProductionQueue().isEmpty()) return false;

        Inventory inv = gameMap.getTownHall().getInventory();

        switch (unitType) {
            case "WORKER":
                return inv.hasEnough(ResourceType.FOOD, 20);

            case "BUILDER":
                return inv.hasEnough(ResourceType.FOOD, 30)
                        && inv.hasEnough(ResourceType.WOOD, 10);

            case "EXPLORER":
                return inv.hasEnough(ResourceType.FOOD, 40)
                        && inv.hasEnough(ResourceType.WOOD, 5);

            case "BORDER_EXPANDER":
                return inv.hasEnough(ResourceType.FOOD, 30)
                        && inv.hasEnough(ResourceType.WOOD, 20)
                        && inv.hasEnough(ResourceType.STONE, 10);

            default:
                return false;
        }
    }

    // =========================================================
    // اجرای ارتقای انبار
    // =========================================================

    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;

        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        inv.consumeResource(ResourceType.WOOD, 100);
        inv.consumeResource(ResourceType.STONE, 50);
        th.upgradeWarehouse();
    }

    // =========================================================
    // اجرای آنلاک تکنولوژی
    // =========================================================

    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;

        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {

            case "STONE_MINE":
                inv.consumeResource(ResourceType.WOOD, 50);
                th.setStoneMineUnlocked(true);
                break;

            case "IRON_MINE":
                inv.consumeResource(ResourceType.WOOD, 100);
                inv.consumeResource(ResourceType.STONE, 50);
                th.setIronMineUnlocked(true);
                break;

            case "PROF_TOOLS":
                inv.consumeResource(ResourceType.WOOD, 100);
                inv.consumeResource(ResourceType.STONE, 100);
                inv.consumeResource(ResourceType.IRON, 50);
                th.setProfessionalToolsUnlocked(true);
                break;

            case "SETTLEMENT":
                // اصلاح گام ۳: آهن هم کسر می‌شود
                inv.consumeResource(ResourceType.WOOD, 150);
                inv.consumeResource(ResourceType.STONE, 100);
                inv.consumeResource(ResourceType.IRON, 50);
                th.setSettlementUnlocked(true);
                break;
        }
    }

    // =========================================================
    // اجرای تولید یونیت
    // =========================================================

    /**
     * شروع تولید یونیت جدید در صف Town Hall.
     *
     * طبق داک: منابع هنگام شروع تولید کسر می‌شوند.
     * یونیت پس از اتمام Turn های مورد نیاز روی هکس TownHall ظاهر می‌شود.
     */
    public void trainUnit(String unitType) {
        if (!canTrainUnit(unitType)) return;

        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        int q = th.getQ();
        int r = th.getR();

        switch (unitType) {

            case "WORKER":
                inv.consumeResource(ResourceType.FOOD, 20);
                th.queueProduction("Worker", 1,
                        () -> gameMap.getUnits().add(new Worker(q, r)));
                break;

            case "BUILDER":
                inv.consumeResource(ResourceType.FOOD, 30);
                inv.consumeResource(ResourceType.WOOD, 10);
                th.queueProduction("Builder", 2,
                        () -> gameMap.getUnits().add(new Builder(q, r)));
                break;

            case "EXPLORER":
                inv.consumeResource(ResourceType.FOOD, 40);
                inv.consumeResource(ResourceType.WOOD, 5);
                th.queueProduction("Explorer", 3,
                        () -> gameMap.getUnits().add(new Explorer(q, r)));
                break;

            case "BORDER_EXPANDER":
                inv.consumeResource(ResourceType.FOOD, 30);
                inv.consumeResource(ResourceType.WOOD, 20);
                inv.consumeResource(ResourceType.STONE, 10);
                th.queueProduction("Border Expander", 3,
                        () -> gameMap.getUnits().add(new BorderExpander(q, r)));
                break;
        }
    }
}