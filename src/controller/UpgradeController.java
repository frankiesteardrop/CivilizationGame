package controller;

import model.*;
import java.util.HashMap;
import java.util.Map;

public class UpgradeController {

    private final GameMap gameMap;

    private interface TechStrategy {
        boolean canUnlock(TownHall th, Inventory inv);
        void unlock(TownHall th, Inventory inv);
    }

    private interface UnitStrategy {
        boolean canTrain(Inventory inv);
        void consumeResources(Inventory inv);
        void refundResources(Inventory inv);
        UnitType getUnitType();
        int getTurnCost();
    }

    private final Map<String, TechStrategy> techStrategies = new HashMap<>();
    private final Map<String, UnitStrategy> unitStrategies = new HashMap<>();

    public UpgradeController(GameMap gameMap) {
        this.gameMap = gameMap;
        initStrategies();
    }

    private void initStrategies() {
        techStrategies.put("STONE_MINE", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return !th.isStoneMineUnlocked() && inv.hasEnough(ResourceType.WOOD, GameConfig.TECH_STONE_MINE_WOOD); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Tech: Stone Mine", GameConfig.TECH_STONE_MINE_TURN_COST, false) {
                    public void execute() { th.setStoneMineUnlocked(true); }
                })) { inv.consumeResource(ResourceType.WOOD, GameConfig.TECH_STONE_MINE_WOOD); }
            }
        });

        techStrategies.put("IRON_MINE", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return th.isStoneMineUnlocked() && !th.isIronMineUnlocked() && inv.hasEnough(ResourceType.WOOD, GameConfig.TECH_IRON_MINE_WOOD) && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_IRON_MINE_STONE); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Tech: Iron Mine", GameConfig.TECH_IRON_MINE_TURN_COST, false) {
                    public void execute() { th.setIronMineUnlocked(true); }
                })) { inv.consumeResource(ResourceType.WOOD, GameConfig.TECH_IRON_MINE_WOOD); inv.consumeResource(ResourceType.STONE, GameConfig.TECH_IRON_MINE_STONE); }
            }
        });

        techStrategies.put("SETTLEMENT", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return th.getLevel() == 1 && inv.hasEnough(ResourceType.WOOD, GameConfig.TH_UPGRADE_LVL2_WOOD) && inv.hasEnough(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL2_STONE); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Upgrade to Settlement", GameConfig.TH_UPGRADE_LVL2_TURN, false) {
                    public void execute() { th.upgradeLevel(); }
                })) { inv.consumeResource(ResourceType.WOOD, GameConfig.TH_UPGRADE_LVL2_WOOD); inv.consumeResource(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL2_STONE); }
            }
        });

        techStrategies.put("PROF_TOOLS", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return th.getLevel() >= 2 && !th.isSteelToolsUnlocked() && inv.hasEnough(ResourceType.IRON, GameConfig.TECH_STEEL_TOOLS_IRON); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Tech: Steel Tools", GameConfig.TECH_STEEL_TOOLS_TURN, false) {
                    public void execute() { th.setSteelToolsUnlocked(true); }
                })) { inv.consumeResource(ResourceType.IRON, GameConfig.TECH_STEEL_TOOLS_IRON); }
            }
        });

        techStrategies.put("SEAFARING", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return th.getLevel() >= 2 && !th.isSeafaringUnlocked() && inv.hasEnough(ResourceType.WOOD, GameConfig.TECH_SEAFARING_WOOD); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Tech: Seafaring", GameConfig.TECH_SEAFARING_TURN, false) {
                    public void execute() { th.setSeafaringUnlocked(true); }
                })) { inv.consumeResource(ResourceType.WOOD, GameConfig.TECH_SEAFARING_WOOD); }
            }
        });

        techStrategies.put("DEFENSIVE_ARCH", new TechStrategy() {
            public boolean canUnlock(TownHall th, Inventory inv) { return th.getLevel() >= 3 && !th.isDefensiveArchUnlocked() && inv.hasEnough(ResourceType.STONE, GameConfig.TECH_DEFENSIVE_ARCH_STONE); }
            public void unlock(TownHall th, Inventory inv) {
                if (th.queueCommand(new ProductionCommand("Tech: Defensive Arch", GameConfig.TECH_DEFENSIVE_ARCH_TURN, false) {
                    public void execute() { th.applyDefensiveArchitecture(); }
                })) { inv.consumeResource(ResourceType.STONE, GameConfig.TECH_DEFENSIVE_ARCH_STONE); }
            }
        });

        unitStrategies.put("WORKER", new UnitStrategy() {
            public boolean canTrain(Inventory inv) { return inv.hasEnough(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST); }
            public void consumeResources(Inventory inv) { inv.consumeResource(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST); }
            public void refundResources(Inventory inv) { inv.addResource(ResourceType.FOOD, GameConfig.WORKER_FOOD_COST); }
            public UnitType getUnitType() { return UnitType.WORKER; }
            public int getTurnCost() { return GameConfig.WORKER_TURN_COST; }
        });
        unitStrategies.put("BUILDER", new UnitStrategy() {
            public boolean canTrain(Inventory inv) { return inv.hasEnough(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST); }
            public void consumeResources(Inventory inv) { inv.consumeResource(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST); inv.consumeResource(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST); }
            public void refundResources(Inventory inv) { inv.addResource(ResourceType.FOOD, GameConfig.BUILDER_FOOD_COST); inv.addResource(ResourceType.WOOD, GameConfig.BUILDER_WOOD_COST); }
            public UnitType getUnitType() { return UnitType.BUILDER; }
            public int getTurnCost() { return GameConfig.BUILDER_TURN_COST; }
        });
        unitStrategies.put("EXPLORER", new UnitStrategy() {
            public boolean canTrain(Inventory inv) { return inv.hasEnough(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST); }
            public void consumeResources(Inventory inv) { inv.consumeResource(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST); inv.consumeResource(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST); }
            public void refundResources(Inventory inv) { inv.addResource(ResourceType.FOOD, GameConfig.EXPLORER_FOOD_COST); inv.addResource(ResourceType.WOOD, GameConfig.EXPLORER_WOOD_COST); }
            public UnitType getUnitType() { return UnitType.EXPLORER; }
            public int getTurnCost() { return GameConfig.EXPLORER_TURN_COST; }
        });
        unitStrategies.put("BORDER_EXPANDER", new UnitStrategy() {
            public boolean canTrain(Inventory inv) { return inv.hasEnough(ResourceType.FOOD, GameConfig.BORDER_EXPANDER_FOOD_COST) && inv.hasEnough(ResourceType.WOOD, GameConfig.BORDER_EXPANDER_WOOD_COST) && inv.hasEnough(ResourceType.STONE, GameConfig.BORDER_EXPANDER_STONE_COST); }
            public void consumeResources(Inventory inv) { inv.consumeResource(ResourceType.FOOD, GameConfig.BORDER_EXPANDER_FOOD_COST); inv.consumeResource(ResourceType.WOOD, GameConfig.BORDER_EXPANDER_WOOD_COST); inv.consumeResource(ResourceType.STONE, GameConfig.BORDER_EXPANDER_STONE_COST); }
            public void refundResources(Inventory inv) { inv.addResource(ResourceType.FOOD, GameConfig.BORDER_EXPANDER_FOOD_COST); inv.addResource(ResourceType.WOOD, GameConfig.BORDER_EXPANDER_WOOD_COST); inv.addResource(ResourceType.STONE, GameConfig.BORDER_EXPANDER_STONE_COST); }
            public UnitType getUnitType() { return UnitType.BORDER_EXPANDER; }
            public int getTurnCost() { return GameConfig.BORDER_EXPANDER_TURN_COST; }
        });
    }

    public boolean canAffordWarehouseUpgrade() {
        TownHall th = gameMap.getTownHall();
        if (th.getLevel() >= 3 || !th.isProductionQueueEmpty()) return false;
        Inventory inv = th.getInventory();
        if (th.getLevel() == 1) return inv.hasEnough(ResourceType.WOOD, GameConfig.TH_UPGRADE_LVL2_WOOD) && inv.hasEnough(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL2_STONE);
        if (th.getLevel() == 2) return inv.hasEnough(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL3_STONE) && inv.hasEnough(ResourceType.IRON, GameConfig.TH_UPGRADE_LVL3_IRON);
        return false;
    }

    public void handleWarehouseUpgrade() {
        if (!canAffordWarehouseUpgrade()) return;
        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        if (th.getLevel() == 1) {
            if (th.queueCommand(new ProductionCommand("Upgrade to Settlement", GameConfig.TH_UPGRADE_LVL2_TURN, false) {
                @Override public void execute() { th.upgradeLevel(); }
            })) {
                inv.consumeResource(ResourceType.WOOD, GameConfig.TH_UPGRADE_LVL2_WOOD);
                inv.consumeResource(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL2_STONE);
            }
        } else if (th.getLevel() == 2) {
            if (th.queueCommand(new ProductionCommand("Upgrade to Capital", GameConfig.TH_UPGRADE_LVL3_TURN, false) {
                @Override public void execute() { th.upgradeLevel(); }
            })) {
                inv.consumeResource(ResourceType.STONE, GameConfig.TH_UPGRADE_LVL3_STONE);
                inv.consumeResource(ResourceType.IRON, GameConfig.TH_UPGRADE_LVL3_IRON);
            }
        }
    }

    public boolean canUnlockTech(String techType) {
        TownHall th = gameMap.getTownHall();
        if (!th.isProductionQueueEmpty()) return false;
        TechStrategy strategy = techStrategies.get(techType);
        return strategy != null && strategy.canUnlock(th, th.getInventory());
    }

    public void unlockTech(String techType) {
        if (!canUnlockTech(techType)) return;
        TechStrategy strategy = techStrategies.get(techType);
        if (strategy != null) strategy.unlock(gameMap.getTownHall(), gameMap.getTownHall().getInventory());
    }

    public boolean canTrainUnit(String unitType) {
        TownHall th = gameMap.getTownHall();
        if (!th.isProductionQueueEmpty() || gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) return false;
        UnitStrategy strategy = unitStrategies.get(unitType);
        return strategy != null && strategy.canTrain(th.getInventory());
    }

    public void trainUnit(String unitType) {
        if (!canTrainUnit(unitType)) return;
        TownHall th = gameMap.getTownHall();
        UnitStrategy strategy = unitStrategies.get(unitType);
        if (strategy == null) return;

        if (th.queueCommand(new ProductionCommand(unitType, strategy.getTurnCost(), true) {
            @Override public void execute() { spawnSpecificUnit(strategy); }
        })) {
            strategy.consumeResources(th.getInventory());
        }
    }

    private void spawnSpecificUnit(UnitStrategy strategy) {
        if (gameMap.getAliveUnitsCount() >= gameMap.getUnitCap()) {
            strategy.refundResources(gameMap.getTownHall().getInventory());
            return;
        }

        // رویداد لحظه‌ای: کسر رضایت در صورت رسیدن به سقف ظرفیت یونیت
        if (gameMap.getAliveUnitsCount() + 1 == gameMap.getUnitCap()) {
            gameMap.getTownHall().addHappiness(-1);
        }

        TownHall th = gameMap.getTownHall();
        Hex spawnHex = gameMap.findEmptySpawnHex(th.getQ(), th.getR());
        int targetQ = spawnHex != null ? spawnHex.getQ() : th.getQ();
        int targetR = spawnHex != null ? spawnHex.getR() : th.getR();

        Unit newUnit = UnitFactory.createUnit(strategy.getUnitType(), targetQ, targetR);
        gameMap.addUnit(newUnit);
    }
}