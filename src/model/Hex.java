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

    // زیرساخت‌های جدید فاز دوم
    private boolean hasRoad;
    private final boolean[] rivers;  // 6 جهت
    private final boolean[] walls;   // 6 جهت
    private final int[] wallHp;      // میزان سلامتی دیوار در هر جهت

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

        this.hasRoad = false;
        this.rivers = new boolean[6];
        this.walls = new boolean[6];
        this.wallHp = new int[6];
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

    // متدهای جاده
    public boolean hasRoad() { return hasRoad; }
    public void setRoad(boolean hasRoad) { this.hasRoad = hasRoad; }

    // متدهای رودخانه (0 تا 5)
    public boolean hasRiver(int dir) { return rivers[dir]; }
    public void setRiver(int dir, boolean hasRiver) { rivers[dir] = hasRiver; }

    // متدهای دیوار (0 تا 5)
    public boolean hasWall(int dir) { return walls[dir]; }
    public void setWall(int dir, boolean hasWall, int hp) {
        walls[dir] = hasWall;
        wallHp[dir] = hp;
    }
    public int getWallHp(int dir) { return wallHp[dir]; }
    public void damageWall(int dir, int amount) {
        if (walls[dir]) {
            wallHp[dir] -= amount;
            if (wallHp[dir] <= 0) {
                walls[dir] = false;
                wallHp[dir] = 0;
            }
        }
    }

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

    public void clearResourceCompletely(ResourceType type) {
        resources.remove(type);
        if (type == ResourceType.FOOD) {
            this.resourceSubtype = ResourceSubtype.NONE;
        }
    }

    public boolean isResourceDepleted() {
        if (resources.isEmpty()) return false;
        for (int amount : resources.values()) {
            if (amount > 0) return false;
        }
        return true;
    }

    public Map<ResourceType, Integer> getResources() { return resources; }
}