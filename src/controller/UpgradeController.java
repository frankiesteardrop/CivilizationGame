package controller;

import model.*;

public class UpgradeController {
    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    // ---- متدهای استعلام وضعیت (برای لایه View) ----

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        return th.getWarehouseUpgradeLevel() < 2 &&
                inv.hasEnough(ResourceType.WOOD, 100) &&
                inv.hasEnough(ResourceType.STONE, 50);
    }

    public boolean canUnlockTech(String techType) {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        switch (techType) {
            case "STONE_MINE": return !th.isStoneMineUnlocked() && inv.hasEnough(ResourceType.WOOD, 50);
            case "IRON_MINE": return th.isStoneMineUnlocked() && !th.isIronMineUnlocked() && inv.hasEnough(ResourceType.WOOD, 100) && inv.hasEnough(ResourceType.STONE, 50);
            case "PROF_TOOLS": return th.isIronMineUnlocked() && !th.isProfessionalToolsUnlocked() && inv.hasEnough(ResourceType.WOOD, 100) && inv.hasEnough(ResourceType.STONE, 100) && inv.hasEnough(ResourceType.IRON, 50);
            case "SETTLEMENT": return !th.isSettlementUnlocked() && inv.hasEnough(ResourceType.WOOD, 200) && inv.hasEnough(ResourceType.STONE, 100);
            default: return false;
        }
    }

    public boolean canTrainUnit(String unitType) {
        if (gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) return false;
        Inventory inv = gameMap.getTownHall().getInventory();
        switch (unitType) {
            case "WORKER": return inv.hasEnough(ResourceType.FOOD, 20);
            case "BUILDER": return inv.hasEnough(ResourceType.FOOD, 30) && inv.hasEnough(ResourceType.WOOD, 10);
            case "EXPLORER": return inv.hasEnough(ResourceType.FOOD, 50);
            case "BORDER_EXPANDER": return inv.hasEnough(ResourceType.FOOD, 40) && inv.hasEnough(ResourceType.WOOD, 20);
            default: return false;
        }
    }

    // ---- متدهای اجرایی (Action) ----

    public void handleWarehouseUpgrade() {
        if (canAffordWarehouseUpgrade()) {
            TownHall th = gameMap.getTownHall();
            Inventory inv = th.getInventory();
            inv.consumeResource(ResourceType.WOOD, 100);
            inv.consumeResource(ResourceType.STONE, 50);
            th.upgradeWarehouse();
        }
    }

    public void unlockTech(String techType) {
        if (canUnlockTech(techType)) {
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
                    inv.consumeResource(ResourceType.WOOD, 200);
                    inv.consumeResource(ResourceType.STONE, 100);
                    th.setSettlementUnlocked(true);
                    break;
            }
        }
    }

    public void trainUnit(String unitType) {
        if (canTrainUnit(unitType)) {
            TownHall th = gameMap.getTownHall();
            Inventory inv = th.getInventory();
            // یونیت‌ها باید دقیقاً روی مختصات تان‌هال ساخته شوند نه (0,0) هاردکد شده
            int q = th.getQ();
            int r = th.getR();

            switch (unitType) {
                case "WORKER":
                    inv.consumeResource(ResourceType.FOOD, 20);
                    th.queueProduction("Worker", 1, () -> gameMap.getUnits().add(new Worker(q, r)));
                    break;
                case "BUILDER":
                    inv.consumeResource(ResourceType.FOOD, 30);
                    inv.consumeResource(ResourceType.WOOD, 10);
                    th.queueProduction("Builder", 2, () -> gameMap.getUnits().add(new Builder(q, r)));
                    break;
                case "EXPLORER":
                    inv.consumeResource(ResourceType.FOOD, 50);
                    th.queueProduction("Explorer", 3, () -> gameMap.getUnits().add(new Explorer(q, r)));
                    break;
                case "BORDER_EXPANDER":
                    inv.consumeResource(ResourceType.FOOD, 40);
                    inv.consumeResource(ResourceType.WOOD, 20);
                    th.queueProduction("Border Expander", 3, () -> gameMap.getUnits().add(new BorderExpander(q, r)));
                    break;
            }
        }
    }
}