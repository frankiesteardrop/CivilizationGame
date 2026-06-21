package model;

public class Hex {
    private int q; // مختصات محوری ستون
    private int r; // مختصات محوری سطر
    private TerrainType terrainType;
    private ResourceType resourceType;
    private int resourceAmount; // مقدار فعلی منبع
    private int resourceCapacity; // ظرفیت کل منبع
    private boolean isExplored; // برای سیستم مه جنگ

    public Hex(int q, int r, TerrainType terrainType, ResourceType resourceType, int resourceCapacity) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resourceType = resourceType;
        this.resourceCapacity = resourceCapacity;
        this.resourceAmount = resourceCapacity; // در ابتدا ظرفیت پر است
        this.isExplored = false; // به صورت پیش فرض ناشناخته است
    }

    // متدهای دسترسی (Getters and Setters)
    public int getQ() { return q; }
    public int getR() { return r; }
    public TerrainType getTerrainType() { return terrainType; }
    public ResourceType getResourceType() { return resourceType; }
    public int getResourceAmount() { return resourceAmount; }
    public boolean isExplored() { return isExplored; }
    public void setExplored(boolean explored) { isExplored = explored; }

    // متد برای استخراج منبع
    public int extractResource(int amount) {
        if (resourceAmount >= amount) {
            resourceAmount -= amount;
            return amount;
        } else {
            int extracted = resourceAmount;
            resourceAmount = 0;
            return extracted;
        }
    }

    public boolean isResourceDepleted() {
        return resourceAmount <= 0 && resourceType != ResourceType.NONE;
    }
}