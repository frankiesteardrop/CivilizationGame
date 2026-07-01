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
}