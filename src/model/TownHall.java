package model;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ساختمان مرکزی (Town Hall).
 *
 * F-36: Happiness دارای soft cap در محدوده -20 تا +20 است.
 * این از overflow بی‌معنی جلوگیری می‌کند (مثلاً با ۱۰ Monument).
 * بازه‌های spec همچنان درست کار می‌کنند:
 *   ≥+3 Golden Age، -2 تا +2 Normal، -3 تا -4 Discontent، ≤-5 Rebellion
 */
public class TownHall extends Building {

    // ─── Happiness bounds (F-36) ──────────────────────────────────────────────
    private static final int HAPPINESS_MIN = -20;
    private static final int HAPPINESS_MAX = +20;

    private int level;
    private final int q;
    private final int r;
    private final Inventory inventory;
    private final Queue<ProductionCommand> productionQueue;

    // ─── Technology flags ─────────────────────────────────────────────────────
    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean steelToolsUnlocked;
    private boolean seafaringUnlocked;
    private boolean defensiveArchUnlocked;

    // ─── Happiness ────────────────────────────────────────────────────────────
    private int happiness;

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q               = q;
        this.r               = r;
        this.level           = 1;
        this.inventory       = new Inventory();
        this.productionQueue = new LinkedList<>();
        this.happiness       = 0;

        this.maxHp = 200;
        this.hp    = 200;

        inventory.addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        inventory.addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        inventory.addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        inventory.addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);
    }

    @Override
    public BuildingType getType() { return BuildingType.TOWN_HALL; }

    public int getQ() { return q; }
    public int getR() { return r; }

    public Inventory getInventory() { return inventory; }

    public void produceSafeguardResources() {
        inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
        inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
    }

    // ─── Level ────────────────────────────────────────────────────────────────

    public int getLevel() { return level; }

    /**
     * F-16: ارتقا به سطح ۲ باعث heal +50 HP می‌شود.
     */
    public void upgradeLevel() {
        level++;
        if (level == 2) {
            inventory.upgradeToLevel2();
            this.heal(50);
        } else if (level == 3) {
            inventory.upgradeToLevel3();
        }
    }

    // ─── Production Queue ─────────────────────────────────────────────────────

    public Queue<ProductionCommand> getProductionQueue() { return productionQueue; }
    public boolean isProductionQueueEmpty()              { return productionQueue.isEmpty(); }

    public boolean queueCommand(ProductionCommand command) {
        if (!productionQueue.isEmpty()) return false;
        productionQueue.offer(command);
        return true;
    }

    public void cancelCurrentProduction() {
        if (!productionQueue.isEmpty()) {
            ProductionCommand cmd = productionQueue.peek();
            if (cmd != null) { cmd.cancel(); productionQueue.poll(); }
        }
    }

    public void advanceProductionQueue(boolean isStarving) {
        if (productionQueue.isEmpty()) return;
        ProductionCommand cmd = productionQueue.peek();
        if (cmd == null || cmd.isCanceled()) { productionQueue.poll(); return; }
        if (isStarving && cmd.isPopulationTask()) return;

        cmd.decrementTurn();
        if (cmd.isCompleted()) {
            productionQueue.poll();
            cmd.execute();
            GameEventDispatcher.fireProductionCompleted(cmd.getName());
        }
    }

    // ─── Happiness (F-36) ─────────────────────────────────────────────────────

    public int getHappiness() { return happiness; }

    /**
     * تغییر رضایت انباشته با اعمال soft cap [-20, +20].
     *
     * F-36: بدون cap، با ۱۰ Monument فعال (+2/ترن هر کدام) مقدار بی‌نهایت
     * بالا می‌رود. محدوده -20 تا +20 تمام حالت‌های spec را پوشش می‌دهد
     * (بالاترین سطح در +3 و پایین‌ترین در -5 است).
     */
    public void addHappiness(int amount) {
        this.happiness = Math.max(HAPPINESS_MIN,
                Math.min(HAPPINESS_MAX, this.happiness + amount));
    }

    // ─── Technology flags ─────────────────────────────────────────────────────

    public boolean isStoneMineUnlocked()            { return stoneMineUnlocked; }
    public void    setStoneMineUnlocked(boolean v)  { this.stoneMineUnlocked = v; }

    public boolean isIronMineUnlocked()             { return ironMineUnlocked; }
    public void    setIronMineUnlocked(boolean v)   { this.ironMineUnlocked = v; }

    public boolean isSettlementUnlocked()           { return level >= 2; }

    public boolean isSteelToolsUnlocked()           { return steelToolsUnlocked; }
    public boolean isProfessionalToolsUnlocked()    { return steelToolsUnlocked; }
    public void    setSteelToolsUnlocked(boolean v) { this.steelToolsUnlocked = v; }

    public boolean isSeafaringUnlocked()            { return seafaringUnlocked; }
    public void    setSeafaringUnlocked(boolean v)  { this.seafaringUnlocked = v; }

    public boolean isDefensiveArchUnlocked()        { return defensiveArchUnlocked; }

    /**
     * F-11: دیوار فیزیکی در UpgradeController ساخته می‌شود (نیاز به GameMap دارد).
     */
    public void applyDefensiveArchitecture() {
        this.defensiveArchUnlocked = true;
        this.setMaxHp(350);
        this.setDefense(30);
        this.heal(350);
    }

    // ─── جوایز مأموریت قبیله ساحلی ─────────────────────────────────────────────────────
    private int discountedDocks = 0;

    public int getDiscountedDocks() { return discountedDocks; }
    public void addDiscountedDock() { discountedDocks++; }
    public void consumeDiscountedDock() { if (discountedDocks > 0) discountedDocks--; }
}