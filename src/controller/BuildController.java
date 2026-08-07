package controller;

import model.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

public class BuildController {

    private final GameMap gameMap;

    // استراتژی‌های چک کردن پیش‌نیازها بدون استفاده از switch-case
    private final Map<BuildingType, Function<TownHall, Boolean>> techRequirements = new HashMap<>();
    private final Map<BuildingType, Predicate<Hex>> terrainRequirements = new HashMap<>();

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
        initRules();
    }

    private void initRules() {
        // قوانین تکنولوژی
        techRequirements.put(BuildingType.STONE_MINE, TownHall::isStoneMineUnlocked);
        techRequirements.put(BuildingType.IRON_MINE, TownHall::isIronMineUnlocked);
        techRequirements.put(BuildingType.SETTLEMENT, TownHall::isSettlementUnlocked);

        // قوانین نوع زمین
        terrainRequirements.put(BuildingType.LUMBER_MILL, hex -> hex.getTerrainType() == TerrainType.FOREST && hex.hasResource(ResourceType.WOOD));
        terrainRequirements.put(BuildingType.FARM, hex -> hex.getTerrainType() == TerrainType.MEADOW && hex.hasResource(ResourceType.FOOD) && (hex.getResourceSubtype() == ResourceSubtype.WHEAT || hex.getResourceSubtype() == ResourceSubtype.RICE));
        terrainRequirements.put(BuildingType.STABLE, hex -> hex.getTerrainType() == TerrainType.PLAINS && hex.hasResource(ResourceType.FOOD) && (hex.getResourceSubtype() == ResourceSubtype.CATTLE || hex.getResourceSubtype() == ResourceSubtype.SHEEP));
        terrainRequirements.put(BuildingType.STONE_MINE, hex -> hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.STONE));
        terrainRequirements.put(BuildingType.IRON_MINE, hex -> hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.IRON));
        terrainRequirements.put(BuildingType.SETTLEMENT, hex -> !hex.hasResource(ResourceType.WOOD) && !hex.hasResource(ResourceType.IRON) && !hex.hasResource(ResourceType.FOOD));
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
        return techRequirements.getOrDefault(type, t -> true).apply(th);
    }

    private boolean isValidTerrainForBuilding(BuildingType type, Hex hex) {
        return terrainRequirements.getOrDefault(type, h -> false).test(hex);
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (!canBuild(type, hex, builder)) return;

        Inventory inv = gameMap.getTownHall().getInventory();
        inv.consumeResource(ResourceType.WOOD, type.getWoodCost());
        inv.consumeResource(ResourceType.STONE, type.getStoneCost());
        inv.consumeResource(ResourceType.IRON, type.getIronCost());

        builder.consumeAP(type.getApCost());
        builder.useCharge();

        // ساخت ساختمان دقیقاً با الگوی Factory
        Building newBuilding = BuildingFactory.createBuilding(type);
        hex.setBuilding(newBuilding);

        gameMap.updateFogOfWar();
        GameEventDispatcher.fireBuildingConstructed(hex);
    }
}