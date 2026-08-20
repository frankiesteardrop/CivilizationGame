package controller;

import model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;

public class BuildController {

    private final GameMap gameMap;
    private final Map<BuildingType, Function<TownHall, Boolean>>    techRequirements    = new HashMap<>();
    private final Map<BuildingType, BiPredicate<Hex, GameMap>>      terrainRequirements = new HashMap<>();

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
        initRules();
    }

    private void initRules() {
        techRequirements.put(BuildingType.STONE_MINE, TownHall::isStoneMineUnlocked);
        techRequirements.put(BuildingType.IRON_MINE,  TownHall::isIronMineUnlocked);
        techRequirements.put(BuildingType.SETTLEMENT, TownHall::isSettlementUnlocked);
        techRequirements.put(BuildingType.BAZAAR, th -> th.getLevel() >= 2);
        techRequirements.put(BuildingType.DOCK,   th -> th.getLevel() >= 2);

        terrainRequirements.put(BuildingType.LUMBER_MILL,
                (hex, map) -> hex.getTerrainType() == TerrainType.FOREST
                        && hex.hasResource(ResourceType.WOOD));

        terrainRequirements.put(BuildingType.FARM,
                (hex, map) -> hex.getTerrainType() == TerrainType.MEADOW
                        && hex.hasResource(ResourceType.FOOD)
                        && (hex.getResourceSubtype() == ResourceSubtype.WHEAT
                        || hex.getResourceSubtype() == ResourceSubtype.RICE));

        terrainRequirements.put(BuildingType.STABLE,
                (hex, map) -> hex.getTerrainType() == TerrainType.PLAINS
                        && hex.hasResource(ResourceType.FOOD)
                        && (hex.getResourceSubtype() == ResourceSubtype.CATTLE
                        || hex.getResourceSubtype() == ResourceSubtype.SHEEP));

        terrainRequirements.put(BuildingType.STONE_MINE,
                (hex, map) -> hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.STONE));

        terrainRequirements.put(BuildingType.IRON_MINE,
                (hex, map) -> hex.getTerrainType() == TerrainType.MOUNTAIN
                        && hex.hasResource(ResourceType.IRON));

        terrainRequirements.put(BuildingType.SETTLEMENT,
                (hex, map) -> !hex.hasResource(ResourceType.WOOD)
                        && !hex.hasResource(ResourceType.IRON)
                        && !hex.hasResource(ResourceType.FOOD));

        terrainRequirements.put(BuildingType.MONUMENT,
                (hex, map) -> hex.getTerrainType() == TerrainType.PLAINS);

        terrainRequirements.put(BuildingType.BAZAAR,
                (hex, map) -> hex.getTerrainType() != TerrainType.SEA
                        && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE);

        terrainRequirements.put(BuildingType.DOCK, (hex, map) -> {
            if (hex.getTerrainType() == TerrainType.SEA
                    || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
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
        if (!hex.isInsideBorder()) return false;
        if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) return false;
        if (hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
        if (builder.getCharges() <= 0 || builder.getCurrentAP() < type.getApCost()) return false;

        TownHall th = gameMap.getTownHall();
        if (!hasRequiredTech(type, th))           return false;
        if (!isValidTerrainForBuilding(type, hex)) return false;

        if (type == BuildingType.DOCK && th.getDiscountedDocks() > 0) {
            return true;
        }

        Inventory inv = th.getInventory();
        return inv.hasEnough(ResourceType.WOOD,  type.getWoodCost())
                && inv.hasEnough(ResourceType.STONE, type.getStoneCost())
                && inv.hasEnough(ResourceType.IRON,  type.getIronCost());
    }

    private boolean hasRequiredTech(BuildingType type, TownHall th) {
        return techRequirements.getOrDefault(type, t -> true).apply(th);
    }

    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex) {
        return terrainRequirements.getOrDefault(type, (h, m) -> false).test(hex, gameMap);
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        TownHall th  = gameMap.getTownHall();
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

        if (type == BuildingType.SETTLEMENT) {
            gameMap.getTownHall().addHappiness(-1);
        }

        gameMap.updateFogOfWar();
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
        GameEventDispatcher.fireUnitStateChanged(builder);
        GameEventDispatcher.fireBuildingConstructed(hex);
        GameEventDispatcher.fireNotification("🛣️ Road successfully constructed!");
    }

    /**
     * I5: اصلاح چک‌های ناقص canBuildWall.
     *
     * چک‌های اضافه‌شده:
     * 1. Builder باید روی hex یا neighbor باشد (نه فقط روی hex)
     * 2. هر دو hex باید کشف شده (explored) باشند
     * 3. حداقل یکی از دو hex باید داخل قلمرو باشد
     * 4. neighbor نباید SEA یا MOUNTAIN_RANGE باشد
     * 5. neighbor باید وجود داشته باشد
     */
    public boolean canBuildWall(Hex hex, int dir, Builder builder) {
        if (hex == null || builder == null || !builder.isAlive()) return false;
        if (dir < 0 || dir > 5) return false;

        // I5: neighbor hex باید وجود داشته باشد
        Hex neighbor = gameMap.getNeighbor(hex, dir);
        if (neighbor == null) return false;

        // I5: Builder باید روی یکی از دو hex مجاور مرز باشد
        boolean builderOnHex      = (builder.getQ() == hex.getQ()      && builder.getR() == hex.getR());
        boolean builderOnNeighbor = (builder.getQ() == neighbor.getQ() && builder.getR() == neighbor.getR());
        if (!builderOnHex && !builderOnNeighbor) return false;

        // I5: هر دو hex باید کشف شده باشند (طبق spec)
        if (!hex.isExplored() || !neighbor.isExplored()) return false;

        // I5: حداقل یکی از دو hex باید داخل قلمرو بازیکن باشد
        if (!hex.isInsideBorder() && !neighbor.isInsideBorder()) return false;

        // دیوار از قبل روی این مرز وجود ندارد
        if (hex.hasWall(dir)) return false;

        // I5: مرزهای مربوط به دریا یا رشته‌کوه غیرمجاز است (هر دو طرف چک می‌شوند)
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
            Building b = hex.getBuilding();

            gameMap.getUnits().stream()
                    .filter(u -> u instanceof Worker)
                    .map(u -> (Worker) u)
                    .filter(w -> w.isStationed()
                            && w.getQ() == hex.getQ()
                            && w.getR() == hex.getR())
                    .forEach(w -> w.eject(gameMap));

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