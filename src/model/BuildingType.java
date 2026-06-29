package model;

/**
 * Enum تعریف انواع ساختمان‌ها و پارامترهای ثابت آن‌ها.
 *
 * پارامترها (به ترتیب):
 * apCost        — هزینه AP برای ساخت (توسط Builder)
 * woodCost      — هزینه چوب برای ساخت
 * stoneCost     — هزینه سنگ برای ساخت
 * ironCost      — هزینه آهن برای ساخت
 * producedResource — نوع منبع تولیدی
 * maxWorkers    — حداکثر کارگر قابل استقرار
 * baseProduction — تولید پایه به ازای هر کارگر در هر Turn
 * upkeepResource — نوع منبع مصرفی برای نگهداری
 * upkeepCost    — مقدار مصرف نگهداری در هر Turn
 */
public enum BuildingType {

    // TownHall: ساختمان اصلی — بدون هزینه ساخت، بدون Upkeep
    TOWN_HALL(
            0, 0, 0, 0,
            ResourceType.NONE, 0, 0,
            ResourceType.NONE, 0
    ),

    // Lumber Mill: فقط روی جنگل — Upkeep از چوب
    LUMBER_MILL(
            1, 10, 0, 0,
            ResourceType.WOOD, 2, 5,
            ResourceType.WOOD, 1
    ),

    // Stone Mine: روی کوهستان با سنگ — نیاز به تکنولوژی — Upkeep از چوب
    STONE_MINE(
            2, 20, 0, 0,
            ResourceType.STONE, 2, 4,
            ResourceType.WOOD, 1
    ),

    // Iron Mine: روی کوهستان با آهن — نیاز به تکنولوژی — Upkeep از چوب
    IRON_MINE(
            2, 30, 10, 0,
            ResourceType.IRON, 2, 2,
            ResourceType.WOOD, 2
    ),

    // Farm: روی سبزه‌زار با گندم/برنج — Upkeep از غذا
    FARM(
            1, 10, 0, 0,
            ResourceType.FOOD, 2, 10,
            ResourceType.FOOD, 1
    ),

    // Stable: روی دشت با حیوانات — Upkeep از چوب
    STABLE(
            2, 20, 0, 0,
            ResourceType.FOOD, 2, 8,
            ResourceType.WOOD, 1
    ),

    // Settlement: روی زمین خالی از منبع — گران‌ترین ساختمان — Upkeep از سنگ
    // طبق داک: نیازمند چوب + سنگ + آهن
    SETTLEMENT(
            3, 80, 60, 30,
            ResourceType.NONE, 0, 0,
            ResourceType.STONE, 3
    );

    // =========================================================
    // فیلدها
    // =========================================================

    private final int apCost;
    private final int woodCost;
    private final int stoneCost;
    private final int ironCost;
    private final ResourceType producedResource;
    private final int maxWorkers;
    private final int baseProduction;
    private final ResourceType upkeepResource;
    private final int upkeepCost;

    // =========================================================
    // Constructor
    // =========================================================

    BuildingType(int apCost, int woodCost, int stoneCost, int ironCost,
                 ResourceType producedResource, int maxWorkers, int baseProduction,
                 ResourceType upkeepResource, int upkeepCost) {
        this.apCost = apCost;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
        this.ironCost = ironCost;
        this.producedResource = producedResource;
        this.maxWorkers = maxWorkers;
        this.baseProduction = baseProduction;
        this.upkeepResource = upkeepResource;
        this.upkeepCost = upkeepCost;
    }

    // =========================================================
    // Getters
    // =========================================================

    public int getApCost()                  { return apCost; }
    public int getWoodCost()                { return woodCost; }
    public int getStoneCost()               { return stoneCost; }
    public int getIronCost()                { return ironCost; }
    public ResourceType getProducedResource(){ return producedResource; }
    public int getMaxWorkers()              { return maxWorkers; }
    public int getBaseProduction()          { return baseProduction; }
    public ResourceType getUpkeepResource() { return upkeepResource; }
    public int getUpkeepCost()              { return upkeepCost; }
}