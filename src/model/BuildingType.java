package model;

public enum BuildingType {

    TOWN_HALL(0, 0, 0, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 2),

    LUMBER_MILL(1, 15, 0, 0, ResourceType.WOOD, 2, 5, ResourceType.WOOD, 1, 1) {
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.FOREST && hex.hasResource(ResourceType.WOOD);
        }
    },

    STONE_MINE(2, 30, 0, 0, ResourceType.STONE, 2, 4, ResourceType.WOOD, 1, 1) {
        @Override
        public boolean hasRequiredTech(TownHall th) {
            return th.isStoneMineUnlocked();
        }
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.STONE);
        }
    },

    IRON_MINE(2, 40, 15, 0, ResourceType.IRON, 2, 2, ResourceType.WOOD, 2, 1) {
        @Override
        public boolean hasRequiredTech(TownHall th) {
            return th.isIronMineUnlocked();
        }
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.MOUNTAIN && hex.hasResource(ResourceType.IRON);
        }
    },

    FARM(1, 15, 0, 0, ResourceType.FOOD, 2, 8, ResourceType.WOOD, 1, 1) {
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.MEADOW
                    && hex.hasResource(ResourceType.FOOD)
                    && (hex.getResourceSubtype() == ResourceSubtype.WHEAT
                    || hex.getResourceSubtype() == ResourceSubtype.RICE);
        }
    },

    STABLE(2, 25, 0, 0, ResourceType.FOOD, 2, 6, ResourceType.WOOD, 1, 1) {
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.PLAINS;
        }
    },

    SETTLEMENT(3, 100, 80, 40, ResourceType.NONE, 0, 0, ResourceType.STONE, 3, 1) {
        @Override
        public boolean hasRequiredTech(TownHall th) {
            return th.isSettlementUnlocked();
        }
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return !hex.hasResource(ResourceType.WOOD)
                    && !hex.hasResource(ResourceType.IRON)
                    && !hex.hasResource(ResourceType.STONE)
                    && !hex.hasResource(ResourceType.FOOD);
        }
    },

    DOCK(2, 30, 0, 0, ResourceType.FOOD, 2, 6, ResourceType.WOOD, 1, 1) {
        @Override
        public boolean hasRequiredTech(TownHall th) {
            return th.getLevel() >= 2;
        }
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            if (hex.getTerrainType() == TerrainType.SEA
                    || hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) return false;
            for (int i = 0; i < 6; i++) {
                Hex neighbor = map.getNeighbor(hex, i);
                if (neighbor != null && neighbor.getTerrainType() == TerrainType.SEA) return true;
            }
            return false;
        }
    },

    MONUMENT(2, 20, 20, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 1) {
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() == TerrainType.PLAINS;
        }
    },

    BAZAAR(2, 30, 30, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 1) {
        @Override
        public boolean hasRequiredTech(TownHall th) {
            return th.getLevel() >= 2;
        }
        @Override
        public boolean isValidTerrain(Hex hex, GameMap map) {
            return hex.getTerrainType() != TerrainType.SEA
                    && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE;
        }
    },

    TRADING_POST(0, 0, 0, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 1),
    TRIBE_CAMP(0, 0, 0, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 2),

    // اصلاح فاز 2: اضافه شدن Outpost به عنوان یک سازه مجزا
    OUTPOST(0, 0, 0, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 2);

    private final int apCost;
    private final int woodCost;
    private final int stoneCost;
    private final int ironCost;
    private final ResourceType producedResource;
    private final int maxWorkers;
    private final int baseProduction;
    private final ResourceType upkeepResource;
    private final int upkeepCost;
    private final int visionRadius;

    BuildingType(int apCost, int woodCost, int stoneCost, int ironCost,
                 ResourceType producedResource, int maxWorkers, int baseProduction,
                 ResourceType upkeepResource, int upkeepCost, int visionRadius) {
        this.apCost = apCost;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
        this.ironCost = ironCost;
        this.producedResource = producedResource;
        this.maxWorkers = maxWorkers;
        this.baseProduction = baseProduction;
        this.upkeepResource = upkeepResource;
        this.upkeepCost = upkeepCost;
        this.visionRadius = visionRadius;
    }

    public int getApCost()                  { return apCost; }
    public int getWoodCost()                { return woodCost; }
    public int getStoneCost()               { return stoneCost; }
    public int getIronCost()                { return ironCost; }
    public ResourceType getProducedResource(){ return producedResource; }
    public int getMaxWorkers()              { return maxWorkers; }
    public int getBaseProduction()          { return baseProduction; }
    public ResourceType getUpkeepResource() { return upkeepResource; }
    public int getUpkeepCost()              { return upkeepCost; }
    public int getVisionRadius()            { return visionRadius; }

    public int getMaxHp() {
        return this == TRIBE_CAMP ? 50 : (this == OUTPOST ? 100 : 100);
    }

    public boolean hasRequiredTech(TownHall th) {
        return true;
    }

    public boolean isValidTerrain(Hex hex, GameMap map) {
        return false;
    }
}