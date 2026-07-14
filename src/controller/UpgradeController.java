package controller;

import model.*;

public class UpgradeController {

    private final GameMap gameMap;

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getWarehouseUpgradeLevel() >= 2) return false;
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
                return !th.isSettlementUnlocked()
                        && inv.hasEnough(ResourceType.WOOD,  GameConfig.TECH_SETTLEMENT_WOOD)
                        && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_SETTLEMENT_STONE)
                        && inv.hasEnough(ResourceType.IRON,  GameConfig.TECH_SETTLEMENT_IRON);
            default:
                return false;
        }
    }

    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        int woodCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_WOOD : GameConfig.WAREHOUSE_LVL2_WOOD;
        int stoneCost = th.getWarehouseUpgradeLevel() == 0 ? GameConfig.WAREHOUSE_LVL1_STONE : GameConfig.WAREHOUSE_LVL2_STONE;

        if (th.queueProduction("Warehouse Upgrade", GameConfig.WAREHOUSE_UPGRADE_TURN_COST, false, th::upgradeWarehouse)) {
            inv.consumeResource(ResourceType.WOOD, woodCost);
            inv.consumeResource(ResourceType.STONE, stoneCost);
        }
    }

    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;
        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (techType) {
            case "STONE_MINE":
                if (th.queueProduction("Tech: Stone Mine", GameConfig.TECH_STONE_MINE_TURN_COST, false,
                        () -> th.setStoneMineUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD, GameConfig.TECH_STONE_MINE_WOOD);
                }
                break;
            case "IRON_MINE":
                if (th.queueProduction("Tech: Iron Mine", GameConfig.TECH_IRON_MINE_TURN_COST, false,
                        () -> th.setIronMineUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_IRON_MINE_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_IRON_MINE_STONE);
                }
                break;
            case "PROF_TOOLS":
                if (th.queueProduction("Tech: Prof. Tools", GameConfig.TECH_PROF_TOOLS_TURN_COST, false,
                        () -> th.setProfessionalToolsUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_PROF_TOOLS_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_PROF_TOOLS_STONE);
                    inv.consumeResource(ResourceType.IRON,  GameConfig.TECH_PROF_TOOLS_IRON);
                }
                break;
            case "SETTLEMENT":
                if (th.queueProduction("Tech: Settlement", GameConfig.TECH_SETTLEMENT_TURN_COST, false,
                        () -> th.setSettlementUnlocked(true))) {
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.TECH_SETTLEMENT_WOOD);
                    inv.consumeResource(ResourceType.STONE, GameConfig.TECH_SETTLEMENT_STONE);
                    inv.consumeResource(ResourceType.IRON,  GameConfig.TECH_SETTLEMENT_IRON);
                }
                break;
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

    public void trainUnit(String unitType) {
        if (!canTrainUnit(unitType)) return;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        switch (unitType) {
            case "WORKER":
                if (th.queueProduction("Worker", GameConfig.WORKER_TURN_COST, true, () -> spawnSpecificUnit("WORKER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST);
                }
                break;
            case "BUILDER":
                if (th.queueProduction("Builder", GameConfig.BUILDER_TURN_COST, true, () -> spawnSpecificUnit("BUILDER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST);
                }
                break;
            case "EXPLORER":
                if (th.queueProduction("Explorer", GameConfig.EXPLORER_TURN_COST, true, () -> spawnSpecificUnit("EXPLORER"))) {
                    inv.consumeResource(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST);
                }
                break;
            case "BORDER_EXPANDER":
                if (th.queueProduction("Border Expander", GameConfig.BORDER_EXPANDER_TURN_COST, true, () -> spawnSpecificUnit("BORDER_EXPANDER"))) {
                    inv.consumeResource(ResourceType.FOOD,  GameConfig.BORDER_EXPANDER_FOOD_COST);
                    inv.consumeResource(ResourceType.WOOD,  GameConfig.BORDER_EXPANDER_WOOD_COST);
                    inv.consumeResource(ResourceType.STONE, GameConfig.BORDER_EXPANDER_STONE_COST);
                }
                break;
        }
    }

    private void spawnSpecificUnit(String unitType) {
        TownHall th = gameMap.getTownHall();
        Hex spawnHex = gameMap.findEmptySpawnHex(th.getQ(), th.getR());
        int targetQ = spawnHex != null ? spawnHex.getQ() : th.getQ();
        int targetR = spawnHex != null ? spawnHex.getR() : th.getR();

        switch(unitType) {
            case "WORKER": gameMap.addUnit(new Worker(targetQ, targetR)); break;
            case "BUILDER": gameMap.addUnit(new Builder(targetQ, targetR)); break;
            case "EXPLORER": gameMap.addUnit(new Explorer(targetQ, targetR)); break;
            case "BORDER_EXPANDER": gameMap.addUnit(new BorderExpander(targetQ, targetR)); break;
        }
    }
}