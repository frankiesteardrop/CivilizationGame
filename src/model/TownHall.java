package model;

import java.util.LinkedList;
import java.util.Queue;


public class TownHall extends Building {

    private final int q;
    private final int r;
    private final Inventory inventory;

    private int warehouseUpgradeLevel;
    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean professionalToolsUnlocked;
    private boolean settlementUnlocked;

    private final Queue<ProductionTask> productionQueue;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q = q;
        this.r = r;
        this.inventory = new Inventory();
        this.productionQueue = new LinkedList<>();

        this.inventory.addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        this.inventory.addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        this.inventory.addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        this.inventory.addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);

        this.warehouseUpgradeLevel     = 0;
        this.stoneMineUnlocked         = false;
        this.ironMineUnlocked          = false;
        this.professionalToolsUnlocked = false;
        this.settlementUnlocked        = false;
    }

    @Override
    public BuildingType getType() {
        return BuildingType.TOWN_HALL;
    }


    public void produceSafeguardResources() {
        this.inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
        this.inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
    }


    public void advanceProductionQueue(boolean isStarving) {
        if (isStarving || productionQueue.isEmpty()) return;

        ProductionTask currentTask = productionQueue.peek();
        currentTask.decrementTurn();

        if (currentTask.isCompleted()) {
            productionQueue.poll();
            currentTask.complete();
            GameEventDispatcher.fireProductionCompleted(currentTask.getName());
        }
    }

    public boolean queueProduction(String itemName, int turnCost, Runnable onComplete) {
        if (!productionQueue.isEmpty()) {
            return false;
        }
        productionQueue.add(new ProductionTask(itemName, turnCost, onComplete));
        return true;
    }

    public boolean isProductionQueueEmpty() {
        return productionQueue.isEmpty();
    }

    public void upgradeWarehouse() {
        if (warehouseUpgradeLevel == 0) {
            warehouseUpgradeLevel = 1;
            inventory.upgradeToLevel1();
        } else if (warehouseUpgradeLevel == 1) {
            warehouseUpgradeLevel = 2;
            inventory.upgradeToLevel2();
        }
    }

    public int getQ()                     { return q; }
    public int getR()                     { return r; }
    public Inventory getInventory()       { return inventory; }
    public int getWarehouseUpgradeLevel() { return warehouseUpgradeLevel; }
    public Queue<ProductionTask> getProductionQueue() { return productionQueue; }

    public boolean isStoneMineUnlocked()              { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean v)       { this.stoneMineUnlocked = v; }
    public boolean isIronMineUnlocked()               { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean v)        { this.ironMineUnlocked = v; }
    public boolean isProfessionalToolsUnlocked()      { return professionalToolsUnlocked; }
    public void setProfessionalToolsUnlocked(boolean v){ this.professionalToolsUnlocked = v; }
    public boolean isSettlementUnlocked()             { return settlementUnlocked; }
    public void setSettlementUnlocked(boolean v)      { this.settlementUnlocked = v; }

    public static class ProductionTask {
        private final String name;
        private int turnsRemaining;
        private final Runnable onComplete;

        public ProductionTask(String name, int turnsRemaining, Runnable onComplete) {
            this.name           = name;
            this.turnsRemaining = turnsRemaining;
            this.onComplete     = onComplete;
        }

        public String getName()        { return name; }
        public int getTurnsRemaining() { return turnsRemaining; }
        public void decrementTurn()    { turnsRemaining--; }
        public boolean isCompleted()   { return turnsRemaining <= 0; }
        public void complete()         { if (onComplete != null) onComplete.run(); }
    }
}