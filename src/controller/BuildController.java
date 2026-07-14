package controller;

import model.*;

public class BuildController {

    private final GameMap gameMap;

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (hex == null || builder == null) return false;
        if (!builder.isAlive()) return false;
        if (builder.getQ() != hex.getQ() || builder.getR() != hex.getR()) return false;
        if (!hex.isInsideBorder()) return false;
        if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) return false;
        if (builder.getCharges() <= 0) return false;
        if (builder.getCurrentAP() < type.getApCost()) return false;

        TownHall th = gameMap.getTownHall();
        if (!hasRequiredTech(type, th)) return false;
        if (!isValidTerrainForBuilding(type, hex)) return false;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD, type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON, type.getIronCost());
    }

    public String getBuildFailReason(BuildingType type, Hex hex, Builder builder) {
        if (builder.getCurrentAP() < type.getApCost()) return " [NO AP]";
        if (builder.getCharges() <= 0) return " [NO CHARGE]";

        TownHall th = gameMap.getTownHall();
        if (!hasRequiredTech(type, th)) return " [NEED TECH]";
        if (!isValidTerrainForBuilding(type, hex)) return " [WRONG TERRAIN]";

        Inventory inv = th.getInventory();
        if (!inv.hasEnough(ResourceType.WOOD, type.getWoodCost())
                || !inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                || !inv.hasEnough(ResourceType.IRON, type.getIronCost())) {
            return " [NO RESOURCE]";
        }

        return "";
    }

    private boolean hasRequiredTech(BuildingType type, TownHall th) {
        switch (type) {
            case STONE_MINE: return th.isStoneMineUnlocked();
            case IRON_MINE: return th.isIronMineUnlocked();
            case SETTLEMENT: return th.isSettlementUnlocked();
            default: return true;
        }
    }

    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex) {
        switch (type) {
            case LUMBER_MILL:
                return hex.getTerrainType() == TerrainType.FOREST
                        && hex.hasResource(ResourceType.WOOD);
            case FARM:
                return hex.getTerrainType() == TerrainType.MEADOW
                        && hex.hasResource(ResourceType.FOOD)
                        && (hex.getResourceSubtype() == ResourceSubtype.WHEAT || hex.getResourceSubtype() == ResourceSubtype.RICE);
            case STABLE:
                return hex.getTerrainType() == TerrainType.PLAINS
                        && hex.hasResource(ResourceType.FOOD)
                        && (hex.getResourceSubtype() == ResourceSubtype.CATTLE || hex.getResourceSubtype() == ResourceSubtype.SHEEP);
            case STONE_MINE:
                return hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.STONE);
            case IRON_MINE:
                return hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.IRON);
            case SETTLEMENT:
                return !hex.hasResource(ResourceType.WOOD) &&
                        !hex.hasResource(ResourceType.IRON) &&
                        !hex.hasResource(ResourceType.FOOD);
            default:
                return false;
        }
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        Inventory inv = gameMap.getTownHall().getInventory();
        inv.consumeResource(ResourceType.WOOD, type.getWoodCost());
        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
        inv.consumeResource(ResourceType.IRON, type.getIronCost());

        builder.consumeAP(type.getApCost());
        builder.useCharge();

        Building newBuilding = createBuilding(type);
        hex.setBuilding(newBuilding);

        gameMap.updateFogOfWar();
        GameEventDispatcher.fireBuildingConstructed(hex);
    }

    private Building createBuilding(BuildingType type) {
        switch (type) {
            case LUMBER_MILL: return new LumberMill();
            case STONE_MINE: return new StoneMine();
            case IRON_MINE: return new IronMine();
            case FARM: return new Farm();
            case STABLE: return new Stable();
            case SETTLEMENT: return new Settlement();
            default: throw new IllegalArgumentException("Unknown building type: " + type);
        }
    }
}