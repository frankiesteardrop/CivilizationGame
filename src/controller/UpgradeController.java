package controller;

import model.*;

/**
 * کنترلر مدیریت آپگریدها، تکنولوژی‌ها و تولید یونیت در Town Hall.
 * * [اصلاح حیاتی گام ۸]:
 * - بررسی Unit Cap حالا با احتساب یونیت‌هایی که در صف تولید (Queue) هستند انجام می‌شود
 * تا بازیکن نتواند از باگ Overpopulation سوءاستفاده کند.
 */
public class UpgradeController {

    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getWarehouseUpgradeLevel() >= 2) return false;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD,  100)
                && inv.hasEnough(ResourceType.STONE, 50);
    }

    public boolean canUnlockTech(String techType) {
        TownHall th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                return !th.isStoneMineUnlocked() && inv.hasEnough(ResourceType.WOOD, 50);
            case "IRON_MINE":
                return th.isStoneMineUnlocked() && !th.isIronMineUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  100)
                        && inv.hasEnough(ResourceType.STONE,  50);
            case "PROF_TOOLS":
                return th.isStoneMineUnlocked() && th.isIronMineUnlocked()
                        && !th.isProfessionalToolsUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  100)
                        && inv.hasEnough(ResourceType.STONE, 100)
                        && inv.hasEnough(ResourceType.IRON,   50);
            case "SETTLEMENT":
                return th.isIronMineUnlocked() && !th.isSettlementUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  150)
                        && inv.hasEnough(ResourceType.STONE, 100)
                        && inv.hasEnough(ResourceType.IRON,   50);
            default:
                return false;
        }
    }

    public boolean canTrainUnit(String unitType) {
        TownHall th = gameMap.getTownHall();

        // [اصلاح حیاتی QA]: محاسبه جمعیت فعلی + یونیت‌هایی که در صف تولید هستند
        int futurePopulation = gameMap.getAliveUnitsCount() + (th.isProductionQueueEmpty() ? 0 : 1);
        if (futurePopulation >= gameMap.getUnitCap()) return false;

        if (!th.isProductionQueueEmpty()) return false;

        Inventory inv = th.getInventory();

        switch (unitType) {
            case "WORKER": return inv.hasEnough(ResourceType.FOOD, 20);
            case "BUILDER": return inv.hasEnough(ResourceType.FOOD, 30) && inv.hasEnough(ResourceType.WOOD, 10);
            case "EXPLORER": return inv.hasEnough(ResourceType.FOOD, 40) && inv.hasEnough(ResourceType.WOOD,  5);
            case "BORDER_EXPANDER": return inv.hasEnough(ResourceType.FOOD,  30) && inv.hasEnough(ResourceType.WOOD,  20) && inv.hasEnough(ResourceType.STONE, 10);
            default: return false;
        }
    }

    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        inv.consumeResource(ResourceType.WOOD,  100);
        inv.consumeResource(ResourceType.STONE,  50);
        th.upgradeWarehouse();
    }

    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                inv.consumeResource(ResourceType.WOOD, 50);
                th.setStoneMineUnlocked(true);
                break;
            case "IRON_MINE":
                inv.consumeResource(ResourceType.WOOD,  100);
                inv.consumeResource(ResourceType.STONE,  50);
                th.setIronMineUnlocked(true);
                break;
            case "PROF_TOOLS":
                inv.consumeResource(ResourceType.WOOD,  100);
                inv.consumeResource(ResourceType.STONE, 100);
                inv.consumeResource(ResourceType.IRON,   50);
                th.setProfessionalToolsUnlocked(true);
                break;
            case "SETTLEMENT":
                inv.consumeResource(ResourceType.WOOD,  150);
                inv.consumeResource(ResourceType.STONE, 100);
                inv.consumeResource(ResourceType.IRON,   50);
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
                boolean queued = th.queueProduction("Worker", 1, () -> gameMap.getUnits().add(new Worker(q, r)));
                if (queued) inv.consumeResource(ResourceType.FOOD, 20);
                break;
            }
            case "BUILDER": {
                boolean queued = th.queueProduction("Builder", 2, () -> gameMap.getUnits().add(new Builder(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD, 30);
                    inv.consumeResource(ResourceType.WOOD, 10);
                }
                break;
            }
            case "EXPLORER": {
                boolean queued = th.queueProduction("Explorer", 3, () -> gameMap.getUnits().add(new Explorer(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD, 40);
                    inv.consumeResource(ResourceType.WOOD,  5);
                }
                break;
            }
            case "BORDER_EXPANDER": {
                boolean queued = th.queueProduction("Border Expander", 3, () -> gameMap.getUnits().add(new BorderExpander(q, r)));
                if (queued) {
                    inv.consumeResource(ResourceType.FOOD,  30);
                    inv.consumeResource(ResourceType.WOOD,  20);
                    inv.consumeResource(ResourceType.STONE, 10);
                }
                break;
            }
        }
    }
}