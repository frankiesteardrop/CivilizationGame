package model;

import java.util.LinkedList;
import java.util.Queue;

// [اصلاح حیاتی گام ۸]: تان‌هال حالا یک ساختمان واقعی است
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
        // فراخوانی سازنده کلاس والد (Building) با ظرفیت صفر کارگر
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q = q;
        this.r = r;
        this.inventory = new Inventory(200);
        this.productionQueue = new LinkedList<>();

        this.inventory.addResource(ResourceType.FOOD, 60);
        this.inventory.addResource(ResourceType.WOOD, 50);
        this.inventory.addResource(ResourceType.STONE, 30);
        this.inventory.addResource(ResourceType.IRON, 0);

        this.warehouseUpgradeLevel = 0;
        this.stoneMineUnlocked = false;
        this.ironMineUnlocked = false;
        this.professionalToolsUnlocked = false;
        this.settlementUnlocked = false;
    }

    // [اصلاح حیاتی گام ۸]: پیاده‌سازی متد اجباری کلاس والد
    @Override
    public BuildingType getType() {
        return BuildingType.TOWN_HALL;
    }

    public void produceSafeguardResources() {
        this.inventory.addResource(ResourceType.WOOD, 1);
        this.inventory.addResource(ResourceType.FOOD, 1);
    }

    public void advanceProductionQueue() {
        if (productionQueue.isEmpty()) return;

        ProductionTask currentTask = productionQueue.peek();
        currentTask.decrementTurn();

        if (currentTask.isCompleted()) {
            productionQueue.poll();
            currentTask.complete();
            GameEventDispatcher.fireProductionCompleted(currentTask.getName());
        }
    }

    public void upgradeWarehouse() {
        if (warehouseUpgradeLevel == 0) {
            warehouseUpgradeLevel = 1;
            inventory.setMaxCapacity(500);
        } else if (warehouseUpgradeLevel == 1) {
            warehouseUpgradeLevel = 2;
            inventory.setMaxCapacity(1000);
        }
    }

    public void queueProduction(String itemName, int turnCost, Runnable onComplete) {
        productionQueue.add(new ProductionTask(itemName, turnCost, onComplete));
    }

    public int getQ() { return q; }
    public int getR() { return r; }
    public Inventory getInventory() { return inventory; }
    public int getWarehouseUpgradeLevel() { return warehouseUpgradeLevel; }
    public Queue<ProductionTask> getProductionQueue() { return productionQueue; }

    public boolean isStoneMineUnlocked() { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean unlocked) { this.stoneMineUnlocked = unlocked; }
    public boolean isIronMineUnlocked() { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean unlocked) { this.ironMineUnlocked = unlocked; }
    public boolean isProfessionalToolsUnlocked() { return professionalToolsUnlocked; }
    public void setProfessionalToolsUnlocked(boolean unlocked) { this.professionalToolsUnlocked = unlocked; }
    public boolean isSettlementUnlocked() { return settlementUnlocked; }
    public void setSettlementUnlocked(boolean unlocked) { this.settlementUnlocked = unlocked; }

    public static class ProductionTask {
        private final String name;
        private int turnsRemaining;
        private final Runnable onComplete;

        public ProductionTask(String name, int turnsRemaining, Runnable onComplete) {
            this.name = name;
            this.turnsRemaining = turnsRemaining;
            this.onComplete = onComplete;
        }

        public String getName() { return name; }
        public int getTurnsRemaining() { return turnsRemaining; }
        public void decrementTurn() { turnsRemaining--; }
        public boolean isCompleted() { return turnsRemaining <= 0; }
        public void complete() { if (onComplete != null) onComplete.run(); }
    }
}