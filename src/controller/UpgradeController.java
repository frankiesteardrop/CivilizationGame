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
}