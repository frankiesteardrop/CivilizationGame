package model;

public class Hex {
    private final int q;
    private final int r;
    private TerrainType terrainType;
    private ResourceType resourceType;
    private int resourceAmount;
    private final int resourceCapacity;
    private boolean isExplored;
    private boolean isInsideBorder; // فیلد جدید سیستم قلمرو و مرز بازی
    private Building building;

    public Hex(int q, int r, TerrainType terrainType, ResourceType resourceType, int resourceCapacity) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resourceType = resourceType;
        this.resourceCapacity = resourceCapacity;
        this.resourceAmount = resourceCapacity;
        this.isExplored = false;
        this.isInsideBorder = false; // به صورت پیش‌فرض خارج از مرز است
        this.building = null;
    }

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

    // متدهای دسترسی جدید برای سیستم قلمرو
    public boolean isInsideBorder() { return isInsideBorder; }
    public void setInsideBorder(boolean insideBorder) { this.isInsideBorder = insideBorder; }

    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }

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