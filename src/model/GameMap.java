package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * کلاس مدیریت ساختار داده‌ای نقشه و یونیت‌های بازی.
 */
public class GameMap {
    private final List<Hex> hexes;
    private final List<Unit> units;
    private final int radius;
    private final Random random;
    private TownHall townHall;

    // متغیرهای سیستم نوبت‌دهی (Turn System)
    private int currentTurn = 1;

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new ArrayList<>();
        this.units = new ArrayList<>();
        this.townHall = new TownHall(0, 0);
        this.random = new Random();

        generateMap();
        spawnInitialUnits();
        updateFogOfWar();
    }

    private void generateMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                if (q == 0 && r == 0) {
                    Hex centerHex = new Hex(q, r, TerrainType.PLAINS, ResourceType.NONE, 0);
                    centerHex.setExplored(true);
                    hexes.add(centerHex);
                    continue;
                }

                TerrainType terrain = getRandomTerrain();
                ResourceType resource = ResourceType.NONE;
                int capacity = 0;

                switch (terrain) {
                    case FOREST:
                        resource = ResourceType.WOOD;
                        capacity = 500;
                        break;
                    case MOUNTAIN:
                        if (random.nextDouble() < 0.3) {
                            resource = ResourceType.IRON;
                            capacity = 200;
                        } else {
                            resource = ResourceType.STONE;
                            capacity = 400;
                        }
                        break;
                    case MEADOW:
                        if (random.nextDouble() < 0.5) {
                            resource = ResourceType.FOOD;
                            capacity = 300;
                        }
                        break;
                    case PLAINS:
                        if (random.nextDouble() < 0.3) {
                            resource = ResourceType.FOOD;
                            capacity = 300;
                        }
                        break;
                }
                hexes.add(new Hex(q, r, terrain, resource, capacity));
            }
        }
    }

    private void spawnInitialUnits() {
        units.add(new Explorer(0, 0));
        units.add(new Builder(0, 0));
        units.add(new Builder(0, 1));
        units.add(new Worker(0, -1));
        units.add(new Worker(1, -1));
    }

    public void updateFogOfWar() {
        for (Hex hex : hexes) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 1) {
                hex.setExplored(true);
            }
        }

        for (Unit unit : units) {
            if (unit.isAlive()) {
                int visionRadius = (unit instanceof Explorer) ? 2 : 1;

                for (Hex hex : hexes) {
                    if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                        hex.setExplored(true);
                    }
                }
            }
        }
    }

    public int getHexDistance(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    private TerrainType getRandomTerrain() {
        TerrainType[] terrains = TerrainType.values();
        return terrains[random.nextInt(terrains.length)];
    }

    public List<Hex> getHexes() { return hexes; }
    public List<Unit> getUnits() { return units; }

    public Hex getHexAt(int q, int r) {
        for (Hex hex : hexes) {
            if (hex.getQ() == q && hex.getR() == r) {
                return hex;
            }
        }
        return null;
    }

    public TownHall getTownHall() {
        return townHall;
    }

    // --- متدهای گام ششم (سیستم نوبت و ظرفیت یونیت) ---
    public int getCurrentTurn() { return currentTurn; }

    public void nextTurn() {
        EconomyManager.processEndTurn(this);
        currentTurn++;
    }

    public int getUnitCap() {
        int cap = 10; // سقف مجاز اولیه
        for (Hex h : hexes) {
            if (h.getBuilding() != null && h.getBuilding().getType() == BuildingType.SETTLEMENT && !h.getBuilding().isDestroyed()) {
                cap += 5; // هر شهرک ۵ واحد به ظرفیت اضافه می‌کند
            }
        }
        return cap;
    }

    public int getAliveUnitsCount() {
        int count = 0;
        for (Unit u : units) {
            if (u.isAlive()) count++;
        }
        return count;
    }
}