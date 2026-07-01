package controller;

import model.*;

/**
 * کنترلر مدیریت ساخت‌وساز.
 */
public class BuildController {

    private final GameMap gameMap;

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (hex == null || builder == null) return false;
        if (!builder.isAlive()) return false;

        if (!hex.isInsideBorder()) return false;

        if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) return false;

        if (builder.getCharges() <= 0)     return false;
        if (builder.getCurrentAP() < type.getApCost()) return false;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        boolean validTerrain = isValidTerrainForBuilding(type, hex, th);
        if (!validTerrain) return false;

        return inv.hasEnough(ResourceType.WOOD,  type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON,  type.getIronCost());
    }

    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex, TownHall th) {
        switch (type) {
            case LUMBER_MILL:
                return hex.getTerrainType() == TerrainType.FOREST
                        && hex.hasResource(ResourceType.WOOD);
            case FARM:
                return hex.getTerrainType() == TerrainType.MEADOW
                        && hex.hasResource(ResourceType.FOOD);
            case STABLE:
                return hex.getTerrainType() == TerrainType.PLAINS
                        && hex.hasResource(ResourceType.FOOD);
            case STONE_MINE:
                return th.isStoneMineUnlocked()
                        && hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.STONE);
            case IRON_MINE:
                return th.isIronMineUnlocked()
                        && hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.IRON);
            case SETTLEMENT:
                boolean hasAnyResource =
                        hex.hasResource(ResourceType.WOOD)  ||
                                hex.hasResource(ResourceType.STONE) ||
                                hex.hasResource(ResourceType.IRON)  ||
                                hex.hasResource(ResourceType.FOOD);
                return th.isSettlementUnlocked() && !hasAnyResource;
            default:
                return false;
        }
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        Inventory inv = gameMap.getTownHall().getInventory();

        // مرحله ۱: پاکسازی صریح ویرانه‌های احتمالی (جلوگیری از Memory Leak و تداخل state)
        if (hex.getBuilding() != null && hex.getBuilding().isDestroyed()) {
            hex.setBuilding(null);
        }

        // مرحله ۲: کسر منابع
        inv.consumeResource(ResourceType.WOOD,  type.getWoodCost());
        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
        inv.consumeResource(ResourceType.IRON,  type.getIronCost());

        // مرحله ۳: کسر AP
        builder.consumeAP(type.getApCost());

        // مرحله ۴: کاهش شارژ (و احتمالاً حذف Builder)
        builder.useCharge();

        // مرحله ۵: نصب ساختمان روی هکس
        Building newBuilding = createBuilding(type);
        hex.setBuilding(newBuilding);

        // مرحله ۶: آپدیت فوری Fog of War
        gameMap.updateFogOfWar();

        // مرحله ۷: اطلاع به لایه View
        GameEventDispatcher.fireBuildingConstructed(hex);
    }

    private Building createBuilding(BuildingType type) {
        switch (type) {
            case LUMBER_MILL: return new LumberMill();
            case STONE_MINE:  return new StoneMine();
            case IRON_MINE:   return new IronMine();
            case FARM:        return new Farm();
            case STABLE:      return new Stable();
            case SETTLEMENT:  return new Settlement();
            default:
                throw new IllegalArgumentException(
                        "Unknown building type: " + type);
        }
    }
}