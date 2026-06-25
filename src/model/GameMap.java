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
        updateFogOfWar(); // به‌روزرسانی اولیه مه‌جنگ در بدو شروع بازی
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

    // --- متد جدید و هوشمند به‌روزرسانی سیستم مه‌جنگ (Fog of War) ---
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
                int visionRadius = 1; // شعاع دید استاندارد کارگر و بیلدر

                // طبق داک پروژه، اکسپلورر دید وسیع‌تری دارد (شعاع دید ۲)
                if (unit instanceof Explorer) {
                    visionRadius = 2;
                }

                for (Hex hex : hexes) {
                    if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                        hex.setExplored(true);
                    }
                }
            }
        }
    }

    // فرمول ریاضی دقیق برای محاسبه فاصله دو هکس در مختصات محوری (Axial)
    public int getHexDistance(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
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
            // خطا حل شد: متغیر h به hex اصلاح شد تا خطای کامپایل برطرف شود
            if (hex.getQ() == q && hex.getR() == r) {
                return hex;
            }
        }
        return null;
    }
}