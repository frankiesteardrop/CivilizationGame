package controller;

import model.*;

public class BuildController {

    private final GameMap gameMap;

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (builder.getQ() != hex.getQ() || builder.getR() != hex.getR()) return false;
        if (!hex.isInsideBorder()) return false;
        if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) return false;
        if (hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        if (builder.getCharges() <= 0 || builder.getCurrentAP() < type.getApCost()) return false;

        TownHall th = gameMap.getTownHall();

        if (!type.hasRequiredTech(th)) return false;
        if (!type.isValidTerrain(hex, gameMap)) return false;

        if (type == BuildingType.DOCK && th.getDiscountedDocks() > 0) {
            return true;
        }

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD,  type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON,  type.getIronCost());
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        TownHall  th  = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        if (type == BuildingType.DOCK && th.getDiscountedDocks() > 0) {
            th.consumeDiscountedDock();
        } else {
            inv.consumeResource(ResourceType.WOOD,  type.getWoodCost());
            inv.consumeResource(ResourceType.STONE, type.getStoneCost());
            inv.consumeResource(ResourceType.IRON,  type.getIronCost());
        }

        builder.consumeAP(type.getApCost());
        builder.useCharge();

        Building newBuilding = BuildingFactory.createBuilding(type);
        hex.setBuilding(newBuilding);

        // لاجیک هاردکد شده کسر رضایت از اینجا حذف شد تا در EconomyController مدیریت شود.

        gameMap.updateFogOfWar();

        // فراخوانی فوری حذف یگان‌های مرده برای از بین بردن اشباح بیلدر (Phantom Builder)
        gameMap.removeDeadUnits();

        GameEventDispatcher.fireBuildingConstructed(hex);
    }

    public boolean canBuildRoad(Hex hex, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (!hex.isInsideBorder() || hex.hasRoad()) return false;
        if (hex.getTerrainType() == TerrainType.SEA
                || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        return builder.getCurrentAP() >= 1 && builder.getCharges() > 0;
    }

    public void buildRoad(Builder builder, Hex hex) {
        if (!canBuildRoad(hex, builder)) return;
        builder.consumeAP(1);
        builder.useCharge();
        hex.setRoad(true);

        gameMap.removeDeadUnits(); // جلوگیری از اشباح بیلدر

        GameEventDispatcher.fireUnitStateChanged(builder);
        GameEventDispatcher.fireBuildingConstructed(hex);
        GameEventDispatcher.fireNotification("🛣️ Road successfully constructed!");
    }

    public boolean canBuildWall(Hex hex, int dir, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (dir < 0 || dir > 5) return false;

        Hex neighbor = gameMap.getNeighbor(hex, dir);
        if (neighbor == null) return false;

        boolean builderOnHex      = (builder.getQ() == hex.getQ()
                && builder.getR() == hex.getR());
        boolean builderOnNeighbor = (builder.getQ() == neighbor.getQ()
                && builder.getR() == neighbor.getR());
        if (!builderOnHex && !builderOnNeighbor) return false;

        if (!hex.isExplored() || !neighbor.isExplored()) return false;
        if (!hex.isInsideBorder() && !neighbor.isInsideBorder()) return false;
        if (hex.hasWall(dir)) return false;

        if (hex.getTerrainType() == TerrainType.SEA
                || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        if (neighbor.getTerrainType() == TerrainType.SEA
                || neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;

        Inventory inv = gameMap.getTownHall().getInventory();
        if (!inv.hasEnough(ResourceType.WOOD,  10)
                || !inv.hasEnough(ResourceType.STONE, 20)) return false;

        return builder.getCurrentAP() >= 2 && builder.getCharges() > 0;
    }

    public void buildWall(Builder builder, Hex hex, int dir) {
        if (!canBuildWall(hex, dir, builder)) return;

        gameMap.getTownHall().getInventory().consumeResource(ResourceType.WOOD,  10);
        gameMap.getTownHall().getInventory().consumeResource(ResourceType.STONE, 20);

        builder.consumeAP(2);
        builder.useCharge();
        hex.setWall(dir, true, 100);

        Hex neighbor = gameMap.getNeighbor(hex, dir);
        if (neighbor != null) neighbor.setWall((dir + 3) % 6, true, 100);

        gameMap.removeDeadUnits(); // جلوگیری از اشباح بیلدر

        GameEventDispatcher.fireUnitStateChanged(builder);
        GameEventDispatcher.fireBuildingConstructed(hex);
        GameEventDispatcher.fireNotification("🧱 Defensive wall successfully constructed!");
    }

    public boolean canDestroy(Hex hex, String type, int dir, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (builder.getCurrentAP() < 1) return false;

        int dist = gameMap.getHexDistance(
                builder.getQ(), builder.getR(), hex.getQ(), hex.getR());
        if (dist > 1) return false;

        if (type.equals("BUILDING")) {
            Building b = hex.getBuilding();
            if (b == null || b.isDestroyed()) return false;
            if (b.getType() == BuildingType.TOWN_HALL)    return false;
            if (b.getType() == BuildingType.TRIBE_CAMP)   return false;
            if (b.getType() == BuildingType.TRADING_POST) return false;
            return true;
        } else if (type.equals("ROAD")) {
            return hex.hasRoad();
        } else if (type.equals("WALL")) {
            return hex.hasWall(dir);
        }
        return false;
    }

    public void destroyStructure(Builder builder, Hex hex, String type, int dir) {
        if (!canDestroy(hex, type, dir, builder)) return;
        builder.consumeAP(1);

        if (type.equals("BUILDING")) {
            hex.setBuilding(null);
            GameEventDispatcher.fireBuildingDestroyed(hex);

        } else if (type.equals("ROAD")) {
            hex.setRoad(false);
            GameEventDispatcher.fireBuildingConstructed(hex);

        } else if (type.equals("WALL")) {
            hex.setWall(dir, false, 0);
            Hex neighbor = gameMap.getNeighbor(hex, dir);
            if (neighbor != null) neighbor.setWall((dir + 3) % 6, false, 0);
            GameEventDispatcher.fireBuildingConstructed(hex);
        }
        GameEventDispatcher.fireUnitStateChanged(builder);
    }
}