package model;

import java.util.HashMap;
import java.util.Map;

public class Hex {
    private final int q;
    private final int r;
    private TerrainType terrainType;

    private final Map<ResourceType, Integer> resources;
    private ResourceSubtype resourceSubtype;

    private boolean isExplored;
    private boolean isVisible;
    private boolean isInsideBorder;
    private Building building;

    public Hex(int q, int r, TerrainType terrainType) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resources = new HashMap<>();
        this.resourceSubtype = ResourceSubtype.NONE;
        this.isExplored = false;
        this.isVisible = false;
        this.isInsideBorder = false;
        this.building = null;
    }

    public int getQ() { return q; }
    public int getR() { return r; }
    public TerrainType getTerrainType() { return terrainType; }
    public void setTerrainType(TerrainType type) { this.terrainType = type; }
    public boolean isExplored() { return isExplored; }
    public void setExplored(boolean explored) { this.isExplored = explored; }
    public boolean isVisible() { return isVisible; }
    public void setVisible(boolean visible) { this.isVisible = visible; }
    public boolean isInsideBorder() { return isInsideBorder; }
    public void setInsideBorder(boolean insideBorder) { this.isInsideBorder = insideBorder; }
    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }

    public ResourceSubtype getResourceSubtype() { return resourceSubtype; }
    public void setResourceSubtype(ResourceSubtype resourceSubtype) { this.resourceSubtype = resourceSubtype; }

    public void addResource(ResourceType type, int amount) {

        if (type != ResourceType.NONE && amount > 0) {
            resources.put(type, resources.getOrDefault(type, 0) + amount);
        }
    }

    public boolean hasResource(ResourceType type) {
        return resources.containsKey(type) && resources.get(type) > 0;
    }

    public int extractResource(ResourceType type, int amount) {
        if (!hasResource(type)) return 0;
        int current = resources.get(type);
        int extracted = Math.min(current, amount);
        resources.put(type, current - extracted);
        return extracted;
    }

    // متد جدید برای حل باگ رندرینگ منابع ارواح (پاکسازی کامل بدون باقی گذاشتن ردِ صفر)
    public void clearResourceCompletely(ResourceType type) {
        resources.remove(type);
        if (type == ResourceType.FOOD) {
            this.resourceSubtype = ResourceSubtype.NONE;
        }
    }

    public boolean isResourceDepleted() {
        if (resources.isEmpty()) return true;
        for (int amount : resources.values()) {
            if (amount > 0) return false;
        }
        return true;
    }

    public Map<ResourceType, Integer> getResources() {
        return resources;
    }
}