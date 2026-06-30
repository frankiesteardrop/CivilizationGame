package model;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ساختمان اصلی بازی — Town Hall.
 *
 * مسئولیت‌ها:
 * - نگهداری انبار منابع (Inventory)
 * - مدیریت صف تولید یونیت‌ها و آپگریدها (تک‌آیتمی — طبق داک)
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

    /**
     * اصلاح گام ۵: صف تولید فقط یک آیتم همزمان نگه می‌دارد.
     * از Queue استفاده می‌کنیم ولی در queueProduction اطمینان می‌دهیم
     * که فقط یک آیتم در صف باشد.
     */
    private final Queue<ProductionTask> productionQueue;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q = q;
        this.r = r;
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
    // تولید Safeguard
    // =========================================================

    public void produceSafeguardResources() {
        this.inventory.addResource(ResourceType.WOOD, 1);
        this.inventory.addResource(ResourceType.FOOD, 1);
    }

    // =========================================================
    // صف تولید — تک‌آیتمی
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
     *
     * اصلاح گام ۵: اگر صف پر باشد (یک آیتم در حال تولید است)،
     * آیتم جدید رد می‌شود. این جلوگیری می‌کند از اینکه بازیکن
     * با چند کلیک سریع چندین آیتم به صف اضافه کند.
     *
     * @return true اگر آیتم با موفقیت به صف اضافه شد
     */
    public boolean queueProduction(String itemName, int turnCost, Runnable onComplete) {
        if (!productionQueue.isEmpty()) {
            // صف پر است — آیتم جدید پذیرفته نمی‌شود
            return false;
        }
        productionQueue.add(new ProductionTask(itemName, turnCost, onComplete));
        return true;
    }

    /**
     * بررسی اینکه آیا صف تولید خالی است.
     * برای استفاده در UpgradeController.canTrainUnit
     */
    public boolean isProductionQueueEmpty() {
        return productionQueue.isEmpty();
    }

    // =========================================================
    // ارتقای انبار
    // =========================================================

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

        public String getName()        { return name; }
        public int getTurnsRemaining() { return turnsRemaining; }
        public void decrementTurn()    { turnsRemaining--; }
        public boolean isCompleted()   { return turnsRemaining <= 0; }
        public void complete()         { if (onComplete != null) onComplete.run(); }
    }
}