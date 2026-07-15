package model;

public enum BuildingType {

    TOWN_HALL(0, 0, 0, 0, ResourceType.NONE, 0, 0, ResourceType.NONE, 0, 2),

    LUMBER_MILL(1, 15, 0, 0, ResourceType.WOOD, 2, 5, ResourceType.WOOD, 1, 1),

    STONE_MINE(2, 30, 0, 0, ResourceType.STONE, 2, 4, ResourceType.WOOD, 1, 1),

    IRON_MINE(2, 40, 15, 0, ResourceType.IRON, 2, 2, ResourceType.WOOD, 2, 1),

    FARM(1, 15, 0, 0, ResourceType.FOOD, 2, 8, ResourceType.WOOD, 1, 1),

    STABLE(2, 25, 0, 0, ResourceType.FOOD, 2, 6, Re  sourceType.WOOD, 1, 1),

    SETTLEMENT(3, 100, 80, 40, ResourceType.NONE, 0, 0, ResourceType.STONE, 3, 1);

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
}