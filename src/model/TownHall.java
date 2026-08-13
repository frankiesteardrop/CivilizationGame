package model;

import java.util.LinkedList;
import java.util.Queue;

/**
 * ساختمان مرکزی (Town Hall) — قلب امپراطوری.
 *
 * طبق spec فاز دوم:
 * - HP پایه: 200
 * - تولید ذاتی: +1 غذا +1 چوب/ترن (produceSafeguardResources)
 * - ظرفیت انبار پایه: 100 واحد برای هر منبع
 * - صف تولید: فقط ۱ دستور فعال در هر لحظه
 * - سطح ۱ → ۲: ارتقای انبار + heal +50 HP
 * - سطح ۲ → ۳: ارتقای انبار
 */
public class TownHall extends Building {

    private int level;
    private final int q;
    private final int r;
    private final Inventory inventory;
    private final Queue<ProductionCommand> productionQueue;

    // ─── Technology flags ─────────────────────────────────────────────────────
    private boolean stoneMineUnlocked;
    private boolean ironMineUnlocked;
    private boolean steelToolsUnlocked;    // = Professional Tools
    private boolean seafaringUnlocked;
    private boolean defensiveArchUnlocked;

    // ─── Happiness (cumulative, starts at 0) ──────────────────────────────────
    private int happiness;

    // ─── Constructor ──────────────────────────────────────────────────────────

    public TownHall(int q, int r) {
        super(BuildingType.TOWN_HALL.getMaxWorkers());
        this.q               = q;
        this.r               = r;
        this.level           = 1;
        this.inventory       = new Inventory();
        this.productionQueue = new LinkedList<>();
        this.happiness       = 0;

        // TownHall HP طبق spec: 200
        this.maxHp = 200;
        this.hp    = 200;

        // منابع اولیه
        inventory.addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        inventory.addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        inventory.addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        inventory.addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);
    }

    @Override
    public BuildingType getType() { return BuildingType.TOWN_HALL; }

    // ─── Position ─────────────────────────────────────────────────────────────

    public int getQ() { return q; }
    public int getR() { return r; }

    // ─── Inventory ────────────────────────────────────────────────────────────

    public Inventory getInventory() { return inventory; }

    /**
     * تولید ذاتی TownHall: +1 غذا +1 چوب در هر ترن.
     * طبق spec: این تولید صرف‌نظر از وضعیت ارتقا ادامه دارد.
     */
    public void produceSafeguardResources() {
        inventory.addResource(ResourceType.FOOD, GameConfig.SAFEGUARD_FOOD_AMOUNT);
        inventory.addResource(ResourceType.WOOD, GameConfig.SAFEGUARD_WOOD_AMOUNT);
    }

    // ─── Level / Upgrade ──────────────────────────────────────────────────────

    public int getLevel() { return level; }

    /**
     * ارتقای سطح TownHall — فراخوانی از ProductionCommand.execute().
     *
     * F-16: ارتقا به سطح ۲ باعث heal +50 HP می‌شود (طبق spec فاز دوم).
     */
    public void upgradeLevel() {
        level++;
        if (level == 2) {
            inventory.upgradeToLevel2();
            // F-16: spec — "ارتقا به سطح ۲ باعث بهبود ۵۰ HP ساختمان مرکزی می‌شود"
            this.heal(50);
        } else if (level == 3) {
            inventory.upgradeToLevel3();
        }
    }

    // ─── Production Queue ─────────────────────────────────────────────────────

    public Queue<ProductionCommand> getProductionQueue() { return productionQueue; }

    public boolean isProductionQueueEmpty() { return productionQueue.isEmpty(); }

    /**
     * اضافه کردن دستور به صف — فقط اگر صف خالی باشد.
     * طبق spec: فقط ۱ دستور فعال در هر لحظه.
     *
     * @return true اگر دستور با موفقیت اضافه شد
     */
    public boolean queueCommand(ProductionCommand command) {
        if (!productionQueue.isEmpty()) return false;
        productionQueue.offer(command);
        return true;
    }

    /**
     * لغو دستور فعلی — منابع مصرف‌شده بازگردانده نمی‌شوند (طبق spec).
     */
    public void cancelCurrentProduction() {
        if (!productionQueue.isEmpty()) {
            ProductionCommand cmd = productionQueue.peek();
            if (cmd != null) {
                cmd.cancel();
                productionQueue.poll();
            }
        }
    }

    /**
     * پیشرفت صف تولید در پایان هر ترن.
     *
     * @param isStarving اگر قحطی فعال باشد، دستورهای جمعیتی منجمد می‌شوند.
     */
    public void advanceProductionQueue(boolean isStarving) {
        if (productionQueue.isEmpty()) return;

        ProductionCommand cmd = productionQueue.peek();
        if (cmd == null || cmd.isCanceled()) {
            productionQueue.poll();
            return;
        }

        // دستورهای جمعیتی (train unit) در حالت قحطی منجمد می‌شوند
        if (isStarving && cmd.isPopulationTask()) return;

        cmd.decrementTurn();

        if (cmd.isCompleted()) {
            productionQueue.poll();
            cmd.execute();
            GameEventDispatcher.fireProductionCompleted(cmd.getName());
        }
    }

    // ─── Happiness ────────────────────────────────────────────────────────────

    /** رضایت انباشته فعلی. */
    public int getHappiness() { return happiness; }

    /** تغییر رضایت انباشته (رویدادهای لحظه‌ای و per-turn). */
    public void addHappiness(int amount) { this.happiness += amount; }

    // ─── Technology flags ─────────────────────────────────────────────────────

    public boolean isStoneMineUnlocked()             { return stoneMineUnlocked; }
    public void    setStoneMineUnlocked(boolean v)   { this.stoneMineUnlocked = v; }

    public boolean isIronMineUnlocked()              { return ironMineUnlocked; }
    public void    setIronMineUnlocked(boolean v)    { this.ironMineUnlocked = v; }

    /** Settlement باز است اگر سطح TH ≥ 2 باشد. */
    public boolean isSettlementUnlocked()            { return level >= 2; }

    /** Steel Tools = Professional Tools — یک flag با دو اسم در codebase. */
    public boolean isSteelToolsUnlocked()            { return steelToolsUnlocked; }
    public boolean isProfessionalToolsUnlocked()     { return steelToolsUnlocked; }
    public void    setSteelToolsUnlocked(boolean v)  { this.steelToolsUnlocked = v; }

    public boolean isSeafaringUnlocked()             { return seafaringUnlocked; }
    public void    setSeafaringUnlocked(boolean v)   { this.seafaringUnlocked = v; }

    public boolean isDefensiveArchUnlocked()         { return defensiveArchUnlocked; }

    /**
     * اعمال تکنولوژی معماری دفاعی روی TownHall.
     * F-11: ساخت دیوار فیزیکی اتوماتیک در UpgradeController انجام می‌شود
     * چون نیاز به GameMap دارد.
     *
     * اثرات این متد (طبق spec):
     * - defense: 10 → 30
     * - maxHP: 200 → 350
     */
    public void applyDefensiveArchitecture() {
        this.defensiveArchUnlocked = true;
        this.setMaxHp(350);
        this.setDefense(30);
        // heal به maxHp جدید (350 - HP فعلی تا حداکثر 350)
        this.heal(350);
    }
}