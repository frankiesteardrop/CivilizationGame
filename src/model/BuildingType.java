package model;

public enum BuildingType {
    // هزینه‌ها: (هزینه AP, هزینه چوب, هزینه سنگ, هزینه آهن, منبع تولیدی, ظرفیت کارگر, تولید پایه, نوع هزینه نگهداری, مقدار نگهداری)
    LUMBER_MILL(1, 0, 0, 0, ResourceType.WOOD, 2, 5, ResourceType.WOOD, 1),
    STONE_MINE(2, 10, 0, 0, ResourceType.STONE, 2, 4, ResourceType.WOOD, 1),
    IRON_MINE(2, 20, 0, 0, ResourceType.IRON, 2, 2, ResourceType.WOOD, 2),
    FARM(1, 0, 0, 0, ResourceType.FOOD, 2, 10, ResourceType.FOOD, 1),
    STABLE(2, 15, 0, 0, ResourceType.FOOD, 2, 8, ResourceType.WOOD, 1),
    SETTLEMENT(3, 50, 50, 20, ResourceType.NONE, 0, 0, ResourceType.STONE, 2);

    private int apCost;
    private int woodCost, stoneCost, ironCost;
    private ResourceType producedResource;
    private int maxWorkers;
    private int baseProduction;
    private ResourceType upkeepResource;
    private int upkeepCost;

    BuildingType(int apCost, int woodCost, int stoneCost, int ironCost, ResourceType producedResource, int maxWorkers, int baseProduction, ResourceType upkeepResource, int upkeepCost) {
        this.apCost = apCost;
        this.woodCost = woodCost;
        this.stoneCost = stoneCost;
        this.ironCost = ironCost;
        this.producedResource = producedResource;
        this.maxWorkers = maxWorkers;
        this.baseProduction = baseProduction;
        this.upkeepResource = upkeepResource;
        this.upkeepCost = upkeepCost;
    }

    public int getApCost() { return apCost; }
    public int getWoodCost() { return woodCost; }
    public int getStoneCost() { return stoneCost; }
    public int getIronCost() { return ironCost; }
    public ResourceType getProducedResource() { return producedResource; }
    public int getMaxWorkers() { return maxWorkers; }
    public int getBaseProduction() { return baseProduction; }
    public ResourceType getUpkeepResource() { return upkeepResource; }
    public int getUpkeepCost() { return upkeepCost; }
}