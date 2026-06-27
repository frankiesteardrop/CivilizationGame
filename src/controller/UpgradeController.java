package controller;

import model.*;

public class UpgradeController {
    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public void handleWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        if (th.getWarehouseUpgradeLevel() < 2 && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 50)) {
            th.upgradeWarehouse();
        }
    }

    public void unlockTech(String techType) {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();
        switch (techType) {
            case "STONE_MINE":
                if (!th.isStoneMineUnlocked() && inv.consumeResource(ResourceType.WOOD, 50)) th.setStoneMineUnlocked(true);
                break;
            case "IRON_MINE":
                if (!th.isIronMineUnlocked() && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 50)) th.setIronMineUnlocked(true);
                break;
            case "PROF_TOOLS":
                if (!th.isProfessionalToolsUnlocked() && inv.consumeResource(ResourceType.WOOD, 100) && inv.consumeResource(ResourceType.STONE, 100) && inv.consumeResource(ResourceType.IRON, 50)) th.setProfessionalToolsUnlocked(true);
                break;
            case "SETTLEMENT":
                if (!th.isSettlementUnlocked() && inv.consumeResource(ResourceType.WOOD, 200) && inv.consumeResource(ResourceType.STONE, 100)) th.setSettlementUnlocked(true);
                break;
        }
    }

    /**
     * سیستم جدید: آموزش و تولید یونیت‌ها از تان‌هال با اضافه شدن به صف تولید
     */
    public void trainUnit(String unitType) {
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        // بررسی گارد سقف یونیت‌ها (Unit Cap)
        if (gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) return;

        // مقادیر بالانس شده و منطقی طبق قوانین بازی برای کسر از انبار
        switch (unitType) {
            case "WORKER":
                if (inv.consumeResource(ResourceType.FOOD, 20)) {
                    th.queueProduction("Worker", 1, () -> gameMap.getUnits().add(new Worker(0, 0)));
                }
                break;
            case "BUILDER":
                if (inv.consumeResource(ResourceType.FOOD, 30) && inv.consumeResource(ResourceType.WOOD, 10)) {
                    th.queueProduction("Builder", 2, () -> gameMap.getUnits().add(new Builder(0, 0)));
                }
                break;
            case "EXPLORER":
                if (inv.consumeResource(ResourceType.FOOD, 50)) {
                    th.queueProduction("Explorer", 3, () -> gameMap.getUnits().add(new Explorer(0, 0)));
                }
                break;
            case "BORDER_EXPANDER":
                if (inv.consumeResource(ResourceType.FOOD, 40) && inv.consumeResource(ResourceType.WOOD, 20)) {
                    th.queueProduction("Border Expander", 3, () -> gameMap.getUnits().add(new BorderExpander(0, 0)));
                }
                break;
        }
    }
}