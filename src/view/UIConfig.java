package view;

import java.awt.*;

public class UIConfig {

    public static final String FONT_SEGOE_UI  = "Segoe UI";
    public static final String FONT_SANS_SERIF = "SansSerif";
    public static final String FONT_CINZEL    = "Georgia";

    public static final Color TERRAIN_PLAINS        = new Color(142, 159,  90);
    public static final Color TERRAIN_PLAINS_LIGHT  = new Color(168, 185, 110);
    public static final Color TERRAIN_PLAINS_DARK   = new Color(108, 126,  65);
    public static final Color TERRAIN_PLAINS_ACCENT = new Color(190, 210, 120);

    public static final Color TERRAIN_FOREST        = new Color( 42,  88,  36);
    public static final Color TERRAIN_FOREST_LIGHT  = new Color( 60, 118,  52);
    public static final Color TERRAIN_FOREST_DARK   = new Color( 25,  58,  20);
    public static final Color TERRAIN_FOREST_CANOPY = new Color( 52, 105,  44);

    public static final Color TERRAIN_MOUNTAIN      = new Color(112, 112, 118);
    public static final Color TERRAIN_MOUNTAIN_LIGHT= new Color(148, 148, 158);
    public static final Color TERRAIN_MOUNTAIN_DARK = new Color( 75,  75,  82);
    public static final Color TERRAIN_MOUNTAIN_ROCK = new Color( 90,  88,  95);
    public static final Color TERRAIN_MOUNTAIN_SNOW = new Color(228, 232, 240);

    public static final Color TERRAIN_MEADOW        = new Color(102, 158,  85);
    public static final Color TERRAIN_MEADOW_LIGHT  = new Color(128, 188, 108);
    public static final Color TERRAIN_MEADOW_DARK   = new Color( 75, 122,  60);
    public static final Color TERRAIN_MEADOW_FLOWER = new Color(220, 180, 100);

    public static final Color TERRAIN_SEA           = new Color( 22,  75, 138);
    public static final Color TERRAIN_SEA_LIGHT     = new Color( 35, 100, 175);
    public static final Color TERRAIN_SEA_DARK      = new Color( 12,  50,  95);
    public static final Color TERRAIN_SEA_FOAM      = new Color( 85, 155, 215);

    public static final Color TERRAIN_MTN_RANGE      = new Color( 48,  46,  52);
    public static final Color TERRAIN_MTN_RANGE_LIGHT= new Color( 68,  65,  72);
    public static final Color TERRAIN_MTN_RANGE_PEAK = new Color( 90,  88,  98);
    public static final Color TERRAIN_MTN_RANGE_SNOW = new Color(200, 205, 215);

    public static final Color VISUAL_ORE_IRON       = new Color(195,  90,  50);
    public static final Color VISUAL_ORE_STONE      = new Color(180, 185, 190);
    public static final Color VISUAL_CROP_WHEAT     = new Color(235, 205,  85);
    public static final Color VISUAL_CROP_RICE      = new Color(155, 210, 120);
    public static final Color VISUAL_ANIMAL_SHEEP   = new Color(245, 245, 250);
    public static final Color VISUAL_ANIMAL_CATTLE  = new Color(130,  80,  45);

    public static final Color FOG_UNEXPLORED     = new Color(  4,   6,   8, 255);
    public static final Color FOG_EXPLORED_DARK  = new Color( 10,  15,  20, 165);
    public static final Color FOG_PATTERN        = new Color( 20,  25,  35,  80);

    public static final Color BORDER_TERRITORY      = new Color( 65, 165, 255, 200);
    public static final Color BORDER_TERRITORY_FILL = new Color( 65, 165, 255,  25);
    public static final Color BORDER_GLOW           = new Color( 80, 180, 255, 140);

    public static final Color HEX_HOVER             = new Color(255, 255, 255,  40);
    public static final Color HEX_SELECTED          = new Color(255, 220,  40, 210);
    public static final Color HEX_SELECTED_GLOW     = new Color(255, 220,  40,  60);
    public static final Color HEX_MOVE_TARGET       = new Color( 60, 210, 120, 175);
    public static final Color HEX_MOVE_FILL         = new Color( 60, 210, 120,  35);
    public static final Color HEX_ATTACK_TARGET     = new Color(225,  55,  55, 175);
    public static final Color HEX_ATTACK_FILL       = new Color(225,  55,  55,  35);

    public static final Color ROAD_COLOR            = new Color(185, 148,  75);
    public static final Color ROAD_BORDER           = new Color(125,  98,  48);
    public static final Color ROAD_SHADOW           = new Color( 80,  62,  30, 100);

    public static final Color RIVER_COLOR           = new Color( 55, 145, 215);
    public static final Color RIVER_DARK            = new Color( 30,  95, 155);
    public static final Color RIVER_SHINE           = new Color(160, 210, 255, 180);

    public static final Color WALL_STONE            = new Color(175, 168, 155);
    public static final Color WALL_SHADOW           = new Color( 95,  90,  82);
    public static final Color WALL_HIGHLIGHT        = new Color(215, 210, 200);

    public static final Color UNIT_SWORDSMAN        = new Color(215,  58,  45);
    public static final Color UNIT_SWORDSMAN_BORDER = new Color(155,  30,  20);
    public static final Color UNIT_ARCHER           = new Color( 45, 158, 225);
    public static final Color UNIT_ARCHER_BORDER    = new Color( 22,  95, 155);
    public static final Color UNIT_CAVALRY          = new Color(155,  75, 210);
    public static final Color UNIT_CAVALRY_BORDER   = new Color( 95,  40, 148);
    public static final Color UNIT_WORKER           = new Color(235, 162,  28);
    public static final Color UNIT_WORKER_BORDER    = new Color(165, 105,  15);
    public static final Color UNIT_BUILDER          = new Color(175, 128,  55);
    public static final Color UNIT_BUILDER_BORDER   = new Color(115,  82,  30);
    public static final Color UNIT_EXPLORER         = new Color( 72, 195, 180);
    public static final Color UNIT_EXPLORER_BORDER  = new Color( 35, 130, 118);
    public static final Color UNIT_EXPANDER         = new Color(115, 195,  72);
    public static final Color UNIT_EXPANDER_BORDER  = new Color( 65, 135,  38);
    public static final Color UNIT_BEAR             = new Color(138,  88,  48);
    public static final Color UNIT_BEAR_BORDER      = new Color( 90,  55,  28);
    public static final Color UNIT_SELECTED_AURA    = new Color(255, 218,  40, 210);
    public static final Color UNIT_STATIONED_AURA   = new Color(255, 200,  50, 150);

    public static final Color BUILDING_TOWN_HALL    = new Color(218, 178,  55);
    public static final Color BUILDING_LUMBER_MILL  = new Color( 95, 158,  55);
    public static final Color BUILDING_FARM         = new Color(175, 210,  75);
    public static final Color BUILDING_STONE_MINE   = new Color(158, 152, 148);
    public static final Color BUILDING_IRON_MINE    = new Color(125, 138, 162);
    public static final Color BUILDING_STABLE       = new Color(175, 138,  75);
    public static final Color BUILDING_SETTLEMENT   = new Color(200, 158,  98);
    public static final Color BUILDING_DOCK         = new Color( 55, 138, 200);
    public static final Color BUILDING_MONUMENT     = new Color(218, 195,  95);
    public static final Color BUILDING_BAZAAR       = new Color(195, 118,  55);
    public static final Color BUILDING_TRADING_POST = new Color(155, 198, 158);
    public static final Color BUILDING_TRIBE_CAMP   = new Color(198,  75,  75);

    public static final Color BUILDING_OUTPOST      = new Color(140,  95, 185);

    public static final Color RESOURCE_FOOD         = new Color(118, 198,  75);
    public static final Color RESOURCE_WOOD         = new Color(158, 108,  52);
    public static final Color RESOURCE_STONE        = new Color(168, 162, 158);
    public static final Color RESOURCE_IRON         = new Color(128, 152, 178);
    public static final Color RESOURCE_FISH         = new Color( 85, 168, 225);
    public static final Color RESOURCE_WHEAT        = new Color(220, 185,  65);
    public static final Color RESOURCE_CATTLE       = new Color(185, 138,  78);
}