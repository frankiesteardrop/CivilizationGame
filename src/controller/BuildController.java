package controller;

import model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class BuildController {

    private final GameMap gameMap;
    private final Map<BuildingType, Function<TownHall, Boolean>> techRequirements = new HashMap<>();

    // تغییر یافت تا GameMap را بگیرد و بتواند همسایه‌ها را بررسی کند (برای ساحل)
    private final Map<BuildingType, BiPredicate<Hex, GameMap>> terrainRequirements = new HashMap<>();

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
        initRules();
    }

    private void initRules() {
        techRequirements.put(BuildingType.STONE_MINE, TownHall::isStoneMineUnlocked);
        techRequirements.put(BuildingType.IRON_MINE, TownHall::isIronMineUnlocked);
        techRequirements.put(BuildingType.SETTLEMENT, TownHall::isSettlementUnlocked);

        terrainRequirements.put(BuildingType.LUMBER_MILL, (hex, map) -> hex.getTerrainType() == TerrainType.FOREST && hex.hasResource(ResourceType.WOOD));
        terrainRequirements.put(BuildingType.FARM, (hex, map) -> hex.getTerrainType() == TerrainType.MEADOW && hex.hasResource(ResourceType.FOOD) && (hex.getResourceSubtype() == ResourceSubtype.WHEAT || hex.getResourceSubtype() == ResourceSubtype.RICE));
        terrainRequirements.put(BuildingType.STABLE, (hex, map) -> hex.getTerrainType() == TerrainType.PLAINS && hex.hasResource(ResourceType.FOOD) && (hex.getResourceSubtype() == ResourceSubtype.CATTLE || hex.getResourceSubtype() == ResourceSubtype.SHEEP));
        terrainRequirements.put(BuildingType.STONE_MINE, (hex, map) -> hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.STONE));
        terrainRequirements.put(BuildingType.IRON_MINE, (hex, map) -> hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.IRON));
        terrainRequirements.put(BuildingType.SETTLEMENT, (hex, map) -> !hex.hasResource(ResourceType.WOOD) && !hex.hasResource(ResourceType.IRON) && !hex.hasResource(ResourceType.FOOD));

        // قوانین ساختمان‌های جدید
        terrainRequirements.put(BuildingType.MONUMENT, (hex, map) -> hex.getTerrainType() == TerrainType.PLAINS);
        terrainRequirements.put(BuildingType.DOCK, (hex, map) -> {
            if (hex.getTerrainType() == TerrainType.SEA || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
            // باید حداقل یک همسایه دریایی داشته باشد (ساحلی باشد)
            for (int i = 0; i < 6; i++) {
                Hex neighbor = map.getNeighbor(hex, i);
                if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA) return true;
            }
            return false;
        });
    }

    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (builder.getQ() != hex.getQ() || builder.getR() != hex.getR()) return false;
        if (!hex.isInsideBorder() || (hex.getBuilding() != null && !hex.getBuilding().isDestroyed())) return false;
        if (builder.getCharges() <= 0 || builder.getCurrentAP() < type.getApCost()) return false;

        TownHall th = gameMap.getTownHall();
        if (!hasRequiredTech(type, th) || !isValidTerrainForBuilding(type, hex)) return false;

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD, type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON, type.getIronCost());
    }

    private boolean hasRequiredTech(BuildingType type, TownHall th) {
        return techRequirements.getOrDefault(type, t -> true).apply(th);
    }

    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex) {
        return terrainRequirements.getOrDefault(type, (h, m) -> false).test(hex, gameMap);
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        Inventory inv = gameMap.getTownHall().getInventory();
        inv.consumeResource(ResourceType.WOOD, type.getWoodCost());
        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
        inv.consumeResource(ResourceType.IRON, type.getIronCost());

        builder.consumeAP(type.getApCost());
        builder.useCharge();

        Building newBuilding = BuildingFactory.createBuilding(type);
        hex.setBuilding(newBuilding);

        gameMap.updateFogOfWar();
        GameEventDispatcher.fireBuildingConstructed(hex);
    }

    // -------------- متدهای جدید زیرساخت (جاده، دیوار) --------------
    public boolean canBuildRoad(Hex hex, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (!hex.isInsideBorder() || hex.hasRoad()) return false;
        if (hex.getTerrainType() == TerrainType.SEA || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        return builder.getCurrentAP() >= 1 && builder.getCharges() > 0;
    }

    public void buildRoad(Builder builder, Hex hex) {
        if (!canBuildRoad(hex, builder)) return;
        builder.consumeAP(1);
        builder.useCharge();
        hex.setRoad(true);
    }

    public boolean canBuildWall(Hex hex, int dir, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (!hex.isInsideBorder() || hex.hasWall(dir)) return false;
        if (hex.getTerrainType() == TerrainType.SEA || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;

        Inventory inv = gameMap.getTownHall().getInventory();
        if (!inv.hasEnough(ResourceType.WOOD, 10) || !inv.hasEnough(ResourceType.STONE, 20)) return false;
        return builder.getCurrentAP() >= 2 && builder.getCharges() > 0;
    }

    public void buildWall(Builder builder, Hex hex, int dir) {
        if (!canBuildWall(hex, dir, builder)) return;
        gameMap.getTownHall().getInventory().consumeResource(ResourceType.WOOD, 10);
        gameMap.getTownHall().getInventory().consumeResource(ResourceType.STONE, 20);

        builder.consumeAP(2);
        builder.useCharge();
        hex.setWall(dir, true, 100);

        // اعمال دیوار برای هکس همسایه (یال مشترک است)
        Hex neighbor = gameMap.getNeighbor(hex, dir);
        if (neighbor != null) neighbor.setWall((dir + 3) % 6, true, 100);
    }

    // -------------- متد جدید تخریب اختیاری --------------
    public boolean canDestroy(Hex hex, String type, int dir, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (builder.getCurrentAP() < 1) return false; // هزینه تخریب 1 AP

        // سازنده باید روی هکس یا مجاور آن باشد
        int dist = gameMap.getHexDistance(builder.getQ(), builder.getR(), hex.getQ(), hex.getR());
        if (dist > 1) return false;

        if (type.equals("BUILDING")) {
            Building b = hex.getBuilding();
            return b != null && !b.isDestroyed() && b.getType() != BuildingType.TOWN_HALL;
        } else if (type.equals("ROAD")) {
            return hex.hasRoad();
        } else if (type.equals("WALL")) {
            return hex.hasWall(dir);
        }
        return false;
    }

    public void destroyStructure(Builder builder, Hex hex, String type, int dir) {
        if (!canDestroy(hex, type, dir, builder)) return;
        builder.consumeAP(1); // منابع بازگردانده نمی‌شوند

        if (type.equals("BUILDING")) {
            Building b = hex.getBuilding();
            // آزادسازی Workerهای مستقر
            gameMap.getUnits().stream()
                    .filter(u -> u instanceof Worker && ((Worker) u).getStationedBuilding() == b)
                    .forEach(u -> ((Worker) u).eject());
            hex.setBuilding(null);
            GameEventDispatcher.fireBuildingDestroyed(hex);
        } else if (type.equals("ROAD")) {
            hex.setRoad(false);
        } else if (type.equals("WALL")) {
            hex.setWall(dir, false, 0);
            Hex neighbor = gameMap.getNeighbor(hex, dir);
            if (neighbor != null) neighbor.setWall((dir + 3) % 6, false, 0);
        }
    }
}