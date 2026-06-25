package model;

/**
 * کلاس مدل برای هر هکس (خانه) در نقشه بازی.
 * توسعه یافته جهت پشتیبانی از ساخت و ساز و قرارگیری سازه‌ها.
 */
public class Hex {
    private final int q; // مختصات محوری ستون
    private final int r; // مختصات محوری سطر
    private TerrainType terrainType;
    private ResourceType resourceType;
    private int resourceAmount; // مقدار فعلی منبع
    private final int resourceCapacity; // ظرفیت کل منبع
    private boolean isExplored; // سیستم مه‌جنگ
    private Building building; // فیلد جدید برای ساختمان مستقر در هکس

    public Hex(int q, int r, TerrainType terrainType, ResourceType resourceType, int resourceCapacity) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resourceType = resourceType;
        this.resourceCapacity = resourceCapacity;
        this.resourceAmount = resourceCapacity;
        this.isExplored = false;
        this.building = null; // در ابتدا هیچ ساختمانی وجود ندارد
    }

    // متدهای دسترسی (Getters and Setters)
    public int getQ() { return q; }
    public int getR() { return r; }

    public TerrainType getTerrainType() { return terrainType; }
    public void setTerrainType(TerrainType type) { this.terrainType = type; }

    public ResourceType getResourceType() { return resourceType; }
    public void setResourceType(ResourceType type) { this.resourceType = type; }

    public int getResourceAmount() { return resourceAmount; }
    public int getResourceCapacity() { return resourceCapacity; }

    public boolean isExplored() { return isExplored; }
    public void setExplored(boolean explored) { isExplored = explored; }

    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }

    /**
     * متد استخراج منبع از هکس
     */
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