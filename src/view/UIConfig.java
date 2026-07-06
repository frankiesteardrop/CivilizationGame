package view;

import java.awt.Color;
import java.awt.Font;


public final class UIConfig {

    private UIConfig() {}

    public static final Color FOREST_TOP_ACTIVE = new Color(60, 180, 60);
    public static final Color FOREST_BASE_ACTIVE = new Color(25, 100, 25);
    public static final Color FOREST_TOP_DEPLETED = new Color(90, 110, 80);
    public static final Color FOREST_BASE_DEPLETED = new Color(55, 70, 50);
    public static final Color FOREST_TOP_NORMAL = new Color(50, 130, 50);
    public static final Color FOREST_BASE_NORMAL = new Color(30, 90, 30);

    public static final Color PLAINS_TOP_ACTIVE = new Color(255, 220, 80);
    public static final Color PLAINS_BASE_ACTIVE = new Color(200, 170, 50);
    public static final Color PLAINS_TOP_NORMAL = new Color(200, 190, 140);
    public static final Color PLAINS_BASE_NORMAL = new Color(160, 148, 100);

    public static final Color MOUNTAIN_TOP_IRON = new Color(140, 120, 100);
    public static final Color MOUNTAIN_BASE_IRON = new Color(80, 65, 55);
    public static final Color MOUNTAIN_TOP_STONE = new Color(190, 190, 185);
    public static final Color MOUNTAIN_BASE_STONE = new Color(120, 118, 115);
    public static final Color MOUNTAIN_TOP_DEPLETED = new Color(130, 128, 125);
    public static final Color MOUNTAIN_BASE_DEPLETED = new Color(80, 78, 75);
    public static final Color MOUNTAIN_TOP_NORMAL = new Color(160, 158, 155);
    public static final Color MOUNTAIN_BASE_NORMAL = new Color(100, 98, 95);

    public static final Color MEADOW_TOP_ACTIVE = new Color(180, 220, 80);
    public static final Color MEADOW_BASE_ACTIVE = new Color(120, 160, 40);
    public static final Color MEADOW_TOP_NORMAL = new Color(130, 155, 90);
    public static final Color MEADOW_BASE_NORMAL = new Color(85, 110, 55);

    public static final Color UNEXPLORED_FILL = new Color(15, 18, 22);
    public static final Color UNEXPLORED_BORDER = new Color(35, 40, 48);
    public static final Color FOG_SHADOW = new Color(10, 15, 20, 155);

    // پالت رنگ‌های هایلایت حرکت (مجاز و غیرمجاز - Action Locking)
    public static final Color MOVE_VALID_FILL = new Color(0, 230, 255, 65);
    public static final Color MOVE_VALID_BORDER = new Color(0, 255, 230, 220);
    public static final Color MOVE_INVALID_FILL = new Color(231, 76, 60, 75);
    public static final Color MOVE_INVALID_BORDER = new Color(255, 80, 80, 230);

    // پالت رنگ‌های آیکون منابع
    public static final Color RES_WOOD_BG = new Color(90, 55, 20, 220);
    public static final Color RES_WOOD_BORDER = new Color(180, 120, 60);
    public static final Color RES_WOOD_TEXT = new Color(255, 200, 120);

    public static final Color RES_STONE_BG = new Color(70, 70, 75, 220);
    public static final Color RES_STONE_BORDER = new Color(180, 180, 185);
    public static final Color RES_STONE_TEXT = new Color(240, 240, 245);

    public static final Color RES_IRON_BG = new Color(80, 55, 30, 220);
    public static final Color RES_IRON_BORDER = new Color(200, 140, 60);
    public static final Color RES_IRON_TEXT = new Color(255, 180, 80);

    public static final Color RES_WHEAT_BG = new Color(180, 140, 20, 220);
    public static final Color RES_WHEAT_BORDER = new Color(240, 200, 60);
    public static final Color RES_WHEAT_TEXT = new Color(255, 245, 180);

    public static final Color RES_RICE_BG = new Color(40, 130, 110, 220);
    public static final Color RES_RICE_BORDER = new Color(80, 200, 170);
    public static final Color RES_RICE_TEXT = new Color(200, 255, 240);

    public static final Color RES_CATTLE_BG = new Color(130, 60, 30, 220);
    public static final Color RES_CATTLE_BORDER = new Color(200, 100, 50);
    public static final Color RES_CATTLE_TEXT = new Color(255, 200, 160);

    public static final Color RES_SHEEP_BG = new Color(140, 130, 110, 220);
    public static final Color RES_SHEEP_BORDER = new Color(210, 200, 180);
    public static final Color RES_SHEEP_TEXT = new Color(255, 250, 240);

    public static final Color RES_FOOD_GENERIC_BG = new Color(30, 90, 30, 220);
    public static final Color RES_FOOD_GENERIC_BORDER = new Color(100, 200, 80);
    public static final Color RES_FOOD_GENERIC_TEXT = new Color(180, 255, 130);

    public static final Color UNIT_EXPLORER = new Color(65, 105, 225);
    public static final Color UNIT_BUILDER = new Color(255, 215, 0);
    public static final Color UNIT_WORKER = new Color(255, 140, 0);
    public static final Color UNIT_EXPANDER = new Color(218, 112, 214);
    public static final Color UNIT_STATIONED_AURA = new Color(255, 165, 0, 150);
    public static final Color UNIT_SELECTED_AURA = new Color(0, 255, 255, 200);

    public static final String FONT_SANS_SERIF = "SansSerif";
    public static final String FONT_SEGOE_UI = "Segoe UI";
}