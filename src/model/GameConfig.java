package model;

/**
 * مرکز تنظیمات و مقادیر ثابت اقتصادی بازی (لایه Model).
 * این کلاس تمام هزینه‌ها و ثابت‌ها را در یک جا متمرکز می‌کند
 * تا از وابستگی‌های نامعقول (Tight Coupling) بین View و Controller جلوگیری شود.
 */
public class GameConfig {
    // هزینه‌های ارتقای انبار
    public static final int WAREHOUSE_LVL1_WOOD = 100;
    public static final int WAREHOUSE_LVL1_STONE = 50;
    public static final int WAREHOUSE_LVL2_WOOD = 200;
    public static final int WAREHOUSE_LVL2_STONE = 100;

    // هزینه‌های تکنولوژی‌ها
    public static final int TECH_STONE_MINE_WOOD = 50;
    public static final int TECH_IRON_MINE_WOOD = 100;
    public static final int TECH_IRON_MINE_STONE = 50;

    public static final int TECH_PROF_TOOLS_WOOD = 100;
    public static final int TECH_PROF_TOOLS_STONE = 100;
    public static final int TECH_PROF_TOOLS_IRON = 50;

    public static final int TECH_SETTLEMENT_WOOD = 150;
    public static final int TECH_SETTLEMENT_STONE = 100;
    public static final int TECH_SETTLEMENT_IRON = 50;

    // هزینه‌های ساخت و تولید یونیت‌ها
    public static final int WORKER_FOOD_COST = 20;
    public static final int WORKER_TURN_COST = 1;

    public static final int BUILDER_FOOD_COST = 30;
    public static final int BUILDER_WOOD_COST = 10;
    public static final int BUILDER_TURN_COST = 2;

    public static final int EXPLORER_FOOD_COST = 40;
    public static final int EXPLORER_WOOD_COST = 5;
    public static final int EXPLORER_TURN_COST = 3;

    public static final int BORDER_EXPANDER_FOOD_COST = 30;
    public static final int BORDER_EXPANDER_WOOD_COST = 20;
    public static final int BORDER_EXPANDER_STONE_COST = 10;
    public static final int BORDER_EXPANDER_TURN_COST = 3;

    public static final int EXPAND_AP_COST = 2;

    // =========================================================
    // زمان‌بندی صف تولید (تعداد نوبت‌ها)
    // =========================================================
    public static final int WAREHOUSE_UPGRADE_TURN_COST = 2;
    public static final int TECH_STONE_MINE_TURN_COST = 2;
    public static final int TECH_IRON_MINE_TURN_COST = 2;
    public static final int TECH_PROF_TOOLS_TURN_COST = 3;
    public static final int TECH_SETTLEMENT_TURN_COST = 3;

    // =========================================================
    // مقادیر بالانس موجودیت‌ها و مکانیک‌ها
    // =========================================================
    public static final int BUILDER_INITIAL_CHARGES = 3;
    public static final int WORKER_STATION_AP_COST = 1;
    public static final int BUILDING_UNPAID_TURNS_TO_DESTROY = 3;
    public static final int BUILDING_VISION_RADIUS = 1;

    public static final int SAFEGUARD_WOOD_AMOUNT = 1;
    public static final int SAFEGUARD_FOOD_AMOUNT = 1;

    // تولید منابع اولیه روی نقشه (Seed)
    public static final int SEED_FOREST_WOOD = 500;
    public static final int SEED_MOUNTAIN_STONE = 400;
    public static final int SEED_MOUNTAIN_IRON = 200;
    public static final int SEED_MEADOW_FOOD = 300;
    public static final int SEED_PLAINS_FOOD = 300;

    // ظرفیت‌های پیش‌فرض و ارتقای انبار
    public static final int DEFAULT_FOOD_CAPACITY  = 200;
    public static final int DEFAULT_WOOD_CAPACITY  = 200;
    public static final int DEFAULT_STONE_CAPACITY = 200;
    public static final int DEFAULT_IRON_CAPACITY  = 200;
    public static final int WAREHOUSE_UPGRADE1_CAPACITY = 500;
    public static final int WAREHOUSE_UPGRADE2_CAPACITY = 1000;

    // [گام حل باگ ۱۵ - اصلاح بالانس اقتصاد اولیه]:
    // افزایش منابع شروع بازی جهت جلوگیری از قفل شدن بازی (Soft-lock) و قحطی زودهنگام
    public static final int STARTING_FOOD = 100;
    public static final int STARTING_WOOD = 80;
    public static final int STARTING_STONE = 40;
    public static final int STARTING_IRON = 0;

    // سقف جمعیت (Unit Cap)
    public static final int UNIT_CAP_BASE = 10;
    public static final int UNIT_CAP_SETTLEMENT_BONUS = 5;
}