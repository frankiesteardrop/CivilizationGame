package model;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class Hex {
    private final int q;
    private final int r;
    private TerrainType terrainType;

    private final Map<ResourceType, Integer> resources;
    private ResourceSubtype resourceSubtype;

    private final Set<String> exploredBy;
    private final Set<String> visibleTo;

    private boolean isInsideBorder;
    private Building building;

    private boolean hasRoad;
    private final boolean[] rivers;
    private final boolean[] walls;
    private final int[] wallHp;

    public Hex(int q, int r, TerrainType terrainType) {
        this.q = q;
        this.r = r;
        this.terrainType = terrainType;
        this.resources = new HashMap<>();
        this.resourceSubtype = ResourceSubtype.NONE;

        this.exploredBy = new HashSet<>();
        this.visibleTo = new HashSet<>();

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

    public boolean isExplored(String playerId) { return exploredBy.contains(playerId); }
    public boolean isVisible(String playerId) { return visibleTo.contains(playerId); }

    public void setExplored(String playerId, boolean explored) {
        if (explored) exploredBy.add(playerId);
        else exploredBy.remove(playerId);
    }

    public void setVisible(String playerId, boolean visible) {
        if (visible) visibleTo.add(playerId);
        else visibleTo.remove(playerId);
    }

    public void clearVisibility() { visibleTo.clear(); }

    public boolean isExplored() { return !exploredBy.isEmpty(); }
    public void setExplored(boolean explored) { setExplored(null, explored); }

    public boolean isVisible() { return !visibleTo.isEmpty(); }
    public void setVisible(boolean visible) { setVisible(null, visible); }


    public boolean isInsideBorder() { return isInsideBorder; }
    public void setInsideBorder(boolean insideBorder) { this.isInsideBorder = insideBorder; }

    public Building getBuilding() { return building; }
    public void setBuilding(Building building) { this.building = building; }

    public boolean hasRoad() { return hasRoad; }
    public void setRoad(boolean hasRoad) { this.hasRoad = hasRoad; }

    public boolean hasRiver(int dir) { return rivers[dir]; }
    public void setRiver(int dir, boolean hasRiver) { rivers[dir] = hasRiver; }

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