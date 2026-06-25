package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameMap {
    private List<Hex> hexes;
    private List<Unit> units; // اضافه شدن لیست یونیت‌ها
    private int radius;
    private Random random;

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new ArrayList<>();
        this.units = new ArrayList<>(); // مقداردهی اولیه لیست
        this.random = new Random();
        generateMap();
        spawnInitialUnits(); // فراخوانی متد استقرار نیروها در شروع بازی
    }

    private void generateMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {
                // هکس مرکزی مخصوص تان هال است و منبعی ندارد
                if (q == 0 && r == 0) {
                    Hex centerHex = new Hex(q, r, TerrainType.PLAINS, ResourceType.NONE, 0);
                    centerHex.setExplored(true); // هکس مرکزی همیشه کشف شده است
                    hexes.add(centerHex);
                    continue;
                }

                TerrainType terrain = getRandomTerrain();
                ResourceType resource = ResourceType.NONE;
                int capacity = 0;

                // اعمال دقیق قوانین پخش منابع
                switch (terrain) {
                    case FOREST:
                        resource = ResourceType.WOOD;
                        capacity = 500;
                        break;
                    case MOUNTAIN:
                        // ۳۰ درصد کوهستان ها آهن دارند و بقیه فقط سنگ
                        if (random.nextDouble() < 0.3) {
                            resource = ResourceType.IRON;
                            capacity = 200;
                        } else {
                            resource = ResourceType.STONE;
                            capacity = 400;
                        }
                        break;
                    case MEADOW:
                        // ۵۰ درصد سبزه زارها منبع غذا دارند
                        if (random.nextDouble() < 0.5) {
                            resource = ResourceType.FOOD;
                            capacity = 300;
                        }
                        break;
                    case PLAINS:
                        // ۳۰ درصد دشت ها حیوانات (منبع غذا) دارند
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

    // متد جدید برای قرار دادن نیروهای اولیه طبق داک پروژه
    private void spawnInitialUnits() {
        units.add(new Explorer(0, 0));
        units.add(new Builder(0, 0));
        units.add(new Builder(0, 1)); // کمی فاصله برای جلوگیری از همپوشانی کامل
        units.add(new Worker(0, -1));
        units.add(new Worker(1, -1));
    }

    private TerrainType getRandomTerrain() {
        TerrainType[] terrains = TerrainType.values();
        return terrains[random.nextInt(terrains.length)];
    }

    public List<Hex> getHexes() {
        return hexes;
    }

    public List<Unit> getUnits() {
        return units; // دسترسی به یونیت‌ها برای بخش گرافیک
    }

    public Hex getHexAt(int q, int r) {
        for (Hex hex : hexes) {
            if (hex.getQ() == q && h.getR() == r) {
                return hex;
            }
        }
        return null;
    }
}