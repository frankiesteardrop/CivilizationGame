package model;

public class TownHall {
    private int q; // مختصات هکسی که تان‌هال توش قرار داره
    private int r;
    private Inventory inventory;

    // وضعیت آپگریدهای انبار (0: اولیه، 1: ارتقا اول، 2: ارتقا دوم)
    private int warehouseUpgradeLevel;

    // وضعیت آنلاک شدن تکنولوژی‌های بازی
    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean professionalToolsUnlocked;
    private boolean settlementUnlocked;

    public TownHall(int q, int r) {
        this.q = q;
        this.r = r;

        // تعیین ظرفیت اولیه انبار (مثلاً 200 واحد برای هر منبع طبق داک)
        this.inventory = new Inventory(200);

        // تنظیم مقادیر اولیه منابع شروع بازی به صورت منطقی
        this.inventory.addResource(ResourceType.FOOD, 60);
        this.inventory.addResource(ResourceType.WOOD, 50);
        this.inventory.addResource(ResourceType.STONE, 30);
        this.inventory.addResource(ResourceType.IRON, 0);

        // در ابتدا تمام تکنولوژی‌ها قفل هستن
        this.warehouseUpgradeLevel = 0;
        this.stoneMineUnlocked = false;
        this.ironMineUnlocked = false;
        this.professionalToolsUnlocked = false;
        this.settlementUnlocked = false;
    }

    // مکانیزم Safeguard: تولید خودکار چوب +1 و غذا +1 در هر ترن
    public void applySafeguard() {
        this.inventory.addResource(ResourceType.WOOD, 1);
        this.inventory.addResource(ResourceType.FOOD, 1);
    }

    // متد ارتقای انبار (حداکثر دو بار انجام می‌شه)
    public void upgradeWarehouse() {
        if (warehouseUpgradeLevel == 0) {
            warehouseUpgradeLevel = 1;
            inventory.setMaxCapacity(500); // ارتقا به ظرفیت 500
        } else if (warehouseUpgradeLevel == 1) {
            warehouseUpgradeLevel = 2;
            inventory.setMaxCapacity(1000); // ارتقا به ظرفیت 1000
        }
    }

    // متدهای دسترسی (Getters & Setters)
    public int getQ() { return q; }
    public int getR() { return r; }
    public Inventory getInventory() { return inventory; }
    public int getWarehouseUpgradeLevel() { return warehouseUpgradeLevel; }

    public boolean isStoneMineUnlocked() { return stoneMineUnlocked; }
    public void setStoneMineUnlocked(boolean unlocked) { this.stoneMineUnlocked = unlocked; }

    public boolean isIronMineUnlocked() { return ironMineUnlocked; }
    public void setIronMineUnlocked(boolean unlocked) { this.ironMineUnlocked = unlocked; }

    public boolean isProfessionalToolsUnlocked() { return professionalToolsUnlocked; }
    public void setProfessionalToolsUnlocked(boolean unlocked) { this.professionalToolsUnlocked = unlocked; }

    public boolean isSettlementUnlocked() { return settlementUnlocked; }
    public void setSettlementUnlocked(boolean unlocked) { this.settlementUnlocked = unlocked; }
}