package controller;

import model.*;

public class BuildController {
    private final GameMap gameMap;

    public BuildController(GameMap gameMap) {
        this.gameMap = gameMap;
    }

    public boolean canBuild(BuildingType type, Hex hex, Builder builder) {
        if (!hex.isInsideBorder() || hex.getBuilding() != null || builder.getCharges() <= 0 || builder.getCurrentAP() < type.getApCost()) {
            return false;
        }

        TownHall th = gameMap.getTownHall();
        Inventory inv = th.getInventory();

        boolean validTerrain = false;
        switch (type) {
            case LUMBER_MILL: validTerrain = hex.getTerrainType() == TerrainType.FOREST; break;
            case FARM: validTerrain = hex.getTerrainType() == TerrainType.MEADOW && hex.hasResource(ResourceType.FOOD); break;
            case STABLE: validTerrain = hex.getTerrainType() == TerrainType.PLAINS && hex.hasResource(ResourceType.FOOD); break;
            case STONE_MINE: validTerrain = th.isStoneMineUnlocked() && hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.STONE); break;
            case IRON_MINE: validTerrain = th.isIronMineUnlocked() && hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.IRON); break;
            case SETTLEMENT: validTerrain = th.isSettlementUnlocked() && hex.isResourceDepleted(); break;
        }

        if (!validTerrain) return false;

        return inv.hasEnough(ResourceType.WOOD, type.getWoodCost()) &&
                inv.hasEnough(ResourceType.STONE, type.getStoneCost()) &&
                inv.hasEnough(ResourceType.IRON, type.getIronCost());
    }

    public void buildStructure(Builder builder, BuildingType type, Hex hex) {
        if (canBuild(type, hex, builder)) {
            Inventory inv = gameMap.getTownHall().getInventory();
            inv.consumeResource(ResourceType.WOOD, type.getWoodCost());
            inv.consumeResource(ResourceType.STONE, type.getStoneCost());
            inv.consumeResource(ResourceType.IRON, type.getIronCost());

            builder.consumeAP(type.getApCost());
            builder.useCharge();

            switch (type) {
                case LUMBER_MILL: hex.setBuilding(new LumberMill()); break;
                case STONE_MINE: hex.setBuilding(new StoneMine()); break;
                case IRON_MINE: hex.setBuilding(new IronMine()); break;
                case FARM: hex.setBuilding(new Farm()); break;
                case STABLE: hex.setBuilding(new Stable()); break;
                case SETTLEMENT: hex.setBuilding(new Settlement()); break;
            }

            // [اصلاح حیاتی گام ۶]: به‌روزرسانی فوری مه‌جنگ پس از ساخت سازه جدید
            gameMap.updateFogOfWar();

            // [اصلاح حیاتی گام ۶]: ارسال سیگنال به گرافیک برای رندر مجدد نقشه
            GameEventDispatcher.fireBuildingConstructed(hex);
        }
    }
}