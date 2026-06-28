package model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameMap {
    private final List<Hex> hexes;
    private final List<Unit> units;
    private final int radius;
    private final Random random;
    private final TownHall townHall;
    private int currentTurn = 1;

    // فلگ وضعیت قحطی — تنظیم می‌شود توسط EconomyManager، خوانده می‌شود در nextTurn
    private boolean isStarving = false;

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

    // =========================================================
    // تولید نقشه
    // =========================================================
    private void generateMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {

                if (q == 0 && r == 0) {
                    Hex centerHex = new Hex(q, r, TerrainType.PLAINS);
                    centerHex.setExplored(true);
                    centerHex.setInsideBorder(true);
                    hexes.add(centerHex);
                    continue;
                }

                TerrainType terrain = getRandomTerrain();
                Hex newHex = new Hex(q, r, terrain);

                switch (terrain) {
                    case FOREST:
                        newHex.addResource(ResourceType.WOOD, 500);
                        break;
                    case MOUNTAIN:
                        newHex.addResource(ResourceType.STONE, 400);
                        if (random.nextDouble() < 0.3) {
                            newHex.addResource(ResourceType.IRON, 200);
                        }
                        break;
                    case MEADOW:
                        if (random.nextDouble() < 0.5) newHex.addResource(ResourceType.FOOD, 300);
                        break;
                    case PLAINS:
                        if (random.nextDouble() < 0.3) newHex.addResource(ResourceType.FOOD, 300);
                        break;
                }

                if (getHexDistance(0, 0, q, r) <= 1) {
                    newHex.setExplored(true);
                    newHex.setInsideBorder(true);
                }
                hexes.add(newHex);
            }
        }
    }

    private void spawnInitialUnits() {
        // پراکنده‌سازی یونیت‌های اولیه روی هکس‌های مجاور تان‌هال
        units.add(new Explorer(1, 0));
        units.add(new Builder(0, 1));
        units.add(new Builder(-1, 1));
        units.add(new Worker(1, -1));
        units.add(new Worker(-1, 0));
    }

    // =========================================================
    // ❤️ هسته اصلی بازی: nextTurn با ترتیب دقیق طبق داک
    // =========================================================
    public void nextTurn() {
        /*
         * ترتیب اجرا طبق داک:
         *  ۱. تجدید AP همه یونیت‌ها (ابتدای نوبت جدید)
         *  ۲. تولید منابع + پیشرفت صف تولید + Upkeep + غذا (EconomyManager)
         *  ۳. اعمال جریمه Starvation روی AP (اگر وضعیت قحطی فعال باشد)
         *  ۴. شماره نوبت افزایش می‌یابد
         */

        // مرحله ۱: تجدید AP همه یونیت‌های زنده برای نوبت جدید
        for (Unit unit : units) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        // مرحله ۲: اجرای کامل چرخه اقتصادی (تولید، صف، upkeep، غذا)
        // EconomyManager فقط isStarving را برمی‌گرداند — resetAP اینجا انجام نمی‌شود
        isStarving = EconomyManager.processEndTurn(this);

        // مرحله ۳: اگر Starvation فعال است، یک واحد AP از همه یونیت‌ها کم می‌شود
        // این بعد از resetAP انجام می‌شود تا AP جدید کاهش یابد، نه AP قدیمی
        if (isStarving) {
            for (Unit unit : units) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        // مرحله ۴: پاک‌سازی یونیت‌های مرده از لیست
        units.removeIf(u -> !u.isAlive());

        // مرحله ۵: افزایش شماره نوبت
        currentTurn++;
    }

    // =========================================================
    // Fog of War
    // =========================================================
    public void updateFogOfWar() {
        // شعاع دید ثابت تان‌هال
        Hex centerHex = getHexAt(townHall.getQ(), townHall.getR());
        if (centerHex != null) centerHex.setExplored(true);

        for (Hex hex : hexes) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 2) {
                hex.setExplored(true);
            }
        }

        // شعاع دید یونیت‌ها
        for (Unit unit : units) {
            if (!unit.isAlive()) continue;
            int visionRadius = unit.getVisionRadius();
            for (Hex hex : hexes) {
                if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                    hex.setExplored(true);
                }
            }
        }

        // شعاع دید ساختمان‌ها (طبق داک، ساختمان‌ها هم vision دارند)
        for (Hex hex : hexes) {
            if (hex.getBuilding() != null && !hex.getBuilding().isDestroyed()) {
                for (Hex other : hexes) {
                    if (getHexDistance(hex.getQ(), hex.getR(), other.getQ(), other.getR()) <= 1) {
                        other.setExplored(true);
                    }
                }
            }
        }
    }

    // =========================================================
    // Border Expansion
    // =========================================================
    public void expandBorderAt(int centerQ, int centerR) {
        Hex centerHex = getHexAt(centerQ, centerR);
        if (centerHex != null && centerHex.isExplored()) {
            centerHex.setInsideBorder(true);
        }

        int[][] directions = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
        for (int[] d : directions) {
            Hex neighbor = getHexAt(centerQ + d[0], centerR + d[1]);
            // طبق داک: فقط هکس‌هایی که قبلاً Explore شده‌اند به مرز اضافه می‌شوند
            if (neighbor != null && neighbor.isExplored()) {
                neighbor.setInsideBorder(true);
            }
        }
    }

    // =========================================================
    // Unit Cap
    // =========================================================
    public int getUnitCap() {
        int cap = 10; // سقف اولیه
        for (Hex h : hexes) {
            Building b = h.getBuilding();
            if (b != null && b.getType() == BuildingType.SETTLEMENT && !b.isDestroyed()) {
                cap += 5;
            }
        }
        return cap;
    }

    // =========================================================
    // Getters و متدهای کمکی
    // =========================================================
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
            if (hex.getQ() == q && hex.getR() == r) return hex;
        }
        return null;
    }

    public TownHall getTownHall() { return townHall; }
    public int getCurrentTurn() { return currentTurn; }
    public boolean isStarving() { return isStarving; }
    public void setStarving(boolean starving) { this.isStarving = starving; }

    public int getAliveUnitsCount() {
        int count = 0;
        for (Unit u : units) {
            if (u.isAlive()) count++;
        }
        return count;
    }
}