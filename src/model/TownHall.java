package model;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ساختمان اصلی بازی — Town Hall.
 *
 * مسئولیت‌ها:
 * - نگهداری انبار منابع (Inventory)
 * - مدیریت صف تولید یونیت‌ها و آپگریدها
 * - نگهداری وضعیت تکنولوژی‌های آنلاک‌شده
 * - تولید Safeguard (حداقل +۱ چوب و +۱ غذا در هر Turn)
 */
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

        // اصلاح گام ۴: استفاده از سازنده بدون پارامتر (per-resource capacity)
        this.inventory = new Inventory();
        this.productionQueue = new LinkedList<>();

        // منابع اولیه بازی — مقادیر منطقی برای شروع
        this.inventory.addResource(ResourceType.FOOD,  60);
        this.inventory.addResource(ResourceType.WOOD,  50);
        this.inventory.addResource(ResourceType.STONE, 30);
        this.inventory.addResource(ResourceType.IRON,   0);

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

    // =========================================================
    // تولید Safeguard — حداقل تضمین‌شده در هر Turn
    // =========================================================

    /**
     * تولید حداقل منابع پایه برای جلوگیری از بی‌منبع شدن کامل بازیکن.
     * طبق داک: +۱ چوب و +۱ غذا در هر Turn — همیشه اجرا می‌شود.
     */
    public void produceSafeguardResources() {
        this.inventory.addResource(ResourceType.WOOD, 1);
        this.inventory.addResource(ResourceType.FOOD, 1);
    }

    // =========================================================
    // صف تولید
    // =========================================================

    /**
     * پیشرفت صف تولید در هر Turn.
     * اگر Task فعلی تکمیل شد، آن را از صف خارج کرده و اجرا می‌کند.
     */
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

    /**
     * افزودن یک Task جدید به صف تولید.
     */
    public void queueProduction(String itemName, int turnCost, Runnable onComplete) {
        productionQueue.add(new ProductionTask(itemName, turnCost, onComplete));
    }

    // =========================================================
    // ارتقای انبار
    // =========================================================

    /**
     * ارتقای انبار — حداکثر ۲ بار قابل انجام.
     * اصلاح گام ۴: از متدهای جدید Inventory استفاده می‌کند.
     */
    public void upgradeWarehouse() {
        if (warehouseUpgradeLevel == 0) {
            warehouseUpgradeLevel = 1;
            inventory.upgradeToLevel1();
        } else if (warehouseUpgradeLevel == 1) {
            warehouseUpgradeLevel = 2;
            inventory.upgradeToLevel2();
        }
    }

    // =========================================================
    // Getters & Setters
    // =========================================================

    public int getQ()                    { return q; }
    public int getR()                    { return r; }
    public Inventory getInventory()      { return inventory; }
    public int getWarehouseUpgradeLevel(){ return warehouseUpgradeLevel; }
    public Queue<ProductionTask> getProductionQueue() { return productionQueue; }

    public boolean isStoneMineUnlocked()            { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean v)     { this.stoneMineUnlocked = v; }
    public boolean isIronMineUnlocked()             { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean v)      { this.ironMineUnlocked = v; }
    public boolean isProfessionalToolsUnlocked()    { return professionalToolsUnlocked; }
    public void setProfessionalToolsUnlocked(boolean v){ this.professionalToolsUnlocked = v; }
    public boolean isSettlementUnlocked()           { return settlementUnlocked; }
    public void setSettlementUnlocked(boolean v)    { this.settlementUnlocked = v; }

    // =========================================================
    // کلاس داخلی: Task صف تولید
    // =========================================================

    public static class ProductionTask {
        private final String name;
        private int turnsRemaining;
        private final Runnable onComplete;

        public ProductionTask(String name, int turnsRemaining, Runnable onComplete) {
            this.name           = name;
            this.turnsRemaining = turnsRemaining;
            this.onComplete     = onComplete;
        }

        public String getName()           { return name; }
        public int getTurnsRemaining()    { return turnsRemaining; }
        public void decrementTurn()       { turnsRemaining--; }
        public boolean isCompleted()      { return turnsRemaining <= 0; }
        public void complete()            { if (onComplete != null) onComplete.run(); }
    }
}