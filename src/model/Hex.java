package model;

import java.util.HashMap;
import java.util.Map;

public class Hex {
    private final int q;
    private final int r;
    private TerrainType terrainType;

    private final Map<ResourceType, Integer> resources;

    private boolean isExplored;
    private boolean isVisible; // وضعیت دید زنده در نوبت جاری
    private boolean isInsideBorder;
    private Building building;

    public Hex(int q, int r, TerrainType terrainType) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resources = new HashMap<>();
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

    public void addResource(ResourceType type, int amount) {
        // [گام حل باگ ۱۴]: به جای جایگزین کردن مقدار، به موجودی فعلی اضافه می‌کنیم
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