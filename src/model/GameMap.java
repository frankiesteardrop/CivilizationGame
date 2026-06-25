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
    private TownHall townHall; // فیلد جدید اضافه شده برای مدیریت مرکز شهر

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new ArrayList<>();
        this.units = new ArrayList<>();
        this.townHall = new TownHall(0, 0); // مقداردهی اولیه TownHall در مرکز نقشه
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
                // هکس مرکزی مخصوص تان هال است و منبع طبیعی ندارد
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
        // ۱. آشکارسازی اطراف تان‌هال در مختصات (0,0) با شعاع دید ۱
        for (Hex hex : hexes) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 1) {
                hex.setExplored(true);
            }
        }

        // ۲. آشکارسازی خانه‌ها بر اساس شعاع دید اختصاصی یونیت‌های زنده
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

    // متد دسترسی تان‌هال برای سیستم‌های مدیریت اقتصاد
    public TownHall getTownHall() {
        return townHall;
    }
}