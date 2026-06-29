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
                    centerHex.setBuilding(this.townHall);
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
                        if (random.nextDouble() < 0.5) {
                            newHex.addResource(ResourceType.FOOD, 300);
                        }
                        break;
                    case PLAINS:
                        if (random.nextDouble() < 0.3) {
                            newHex.addResource(ResourceType.FOOD, 300);
                        }
                        break;
                }

                // هکس‌های شعاع ۱ از مرکز از ابتدا کشف‌شده و داخل مرز هستند
                if (getHexDistance(townHall.getQ(), townHall.getR(), q, r) <= 1) {
                    newHex.setExplored(true);
                    newHex.setInsideBorder(true);
                }
                hexes.add(newHex);
            }
        }

        ensureStartingResources();
    }

    /**
     * تضمین وجود حداقل یک هکس جنگلی در شعاع ۲ هکسی از TownHall.
     * طبق داک: فاصله هکس جنگل از TownHall نباید بیشتر از ۲ باشد.
     */
    private void ensureStartingResources() {
        boolean hasForestNear = false;
        List<Hex> availableCandidates = new ArrayList<>();

        for (Hex hex : hexes) {
            int dist = getHexDistance(townHall.getQ(), townHall.getR(),
                    hex.getQ(), hex.getR());
            if (dist > 0 && dist <= 2) {
                if (hex.getTerrainType() == TerrainType.FOREST) {
                    hasForestNear = true;
                    break;
                }
                if (hex.getTerrainType() != TerrainType.MOUNTAIN) {
                    availableCandidates.add(hex);
                }
            }
        }

        if (!hasForestNear && !availableCandidates.isEmpty()) {
            Hex targetHex = availableCandidates.get(
                    random.nextInt(availableCandidates.size()));
            targetHex.setTerrainType(TerrainType.FOREST);

            // پاک کردن منابع قبلی (مثلاً FOOD از دشت)
            if (targetHex.hasResource(ResourceType.FOOD)) {
                targetHex.extractResource(ResourceType.FOOD, Integer.MAX_VALUE);
            }

            targetHex.addResource(ResourceType.WOOD, 500);
        }
    }

    private void spawnInitialUnits() {
        units.add(new Explorer(1, 0));
        units.add(new Builder(0, 1));
        units.add(new Builder(-1, 1));
        units.add(new Worker(1, -1));
        units.add(new Worker(-1, 0));
    }

    // =========================================================
    // چرخه نوبت — ترتیب صحیح طبق داک
    // =========================================================

    /**
     * اجرای چرخه پایان نوبت به ترتیب دقیق طبق داک:
     *
     * ۱. تجدید AP همه یونیت‌های زنده
     * ۲. تولید منابع + پیشرفت صف تولید (فاز ۱ و ۲ در EconomyManager)
     * ۳. کسر Upkeep (فاز ۳ در EconomyManager)
     * ۴. کسر غذا + تشخیص Starvation (فاز ۴ در EconomyManager)
     * ۵. اعمال پنالتی AP در صورت Starvation
     * ۶. حذف یونیت‌های مرده از لیست
     * ۷. افزایش شمارنده نوبت
     */
    public void nextTurn() {
        // مرحله ۱: تجدید AP همه یونیت‌های زنده (طبق داک — اولین کار در ابتدای نوبت جدید)
        for (Unit unit : units) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        // مراحل ۲ تا ۴: چرخه اقتصادی (ترتیب داخلی در EconomyManager)
        isStarving = EconomyManager.processEndTurn(this);

        // مرحله ۵: اعمال پنالتی Starvation روی AP یونیت‌ها
        // (بعد از resetAP اجرا می‌شود تا AP همیشه کمتر از maxAP باشد)
        if (isStarving) {
            for (Unit unit : units) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        // مرحله ۶: پاکسازی یونیت‌های مرده از لیست فعال
        units.removeIf(u -> !u.isAlive());

        // مرحله ۷: پیشرفت شمارنده نوبت
        currentTurn++;
    }

    // =========================================================
    // سیستم مه‌جنگ (Fog of War)
    // =========================================================

    /**
     * آپدیت کامل Fog of War بر اساس موقعیت همه یونیت‌ها و ساختمان‌ها.
     * اصلاح: استفاده از مختصات واقعی TownHall به جای هاردکد (0,0)
     */
    public void updateFogOfWar() {
        // دید TownHall — شعاع ۲ هکس از موقعیت واقعی آن
        for (Hex hex : hexes) {
            if (getHexDistance(townHall.getQ(), townHall.getR(),
                    hex.getQ(), hex.getR()) <= 2) {
                hex.setExplored(true);
            }
        }

        // دید یونیت‌های زنده
        for (Unit unit : units) {
            if (!unit.isAlive()) continue;
            int visionRadius = unit.getVisionRadius();
            for (Hex hex : hexes) {
                if (getHexDistance(unit.getQ(), unit.getR(),
                        hex.getQ(), hex.getR()) <= visionRadius) {
                    hex.setExplored(true);
                }
            }
        }

        // دید ساختمان‌های فعال (شعاع ۱ هکس)
        for (Hex hex : hexes) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed()) {
                for (Hex other : hexes) {
                    if (getHexDistance(hex.getQ(), hex.getR(),
                            other.getQ(), other.getR()) <= 1) {
                        other.setExplored(true);
                    }
                }
            }
        }
    }

    // =========================================================
    // توسعه مرز
    // =========================================================

    /**
     * افزودن هکس مرکزی و ۶ همسایه آن به مرزهای امپراتوری.
     * فقط هکس‌هایی که قبلاً کشف شده‌اند می‌توانند به مرز اضافه شوند.
     */
    public void expandBorderAt(int centerQ, int centerR) {
        Hex centerHex = getHexAt(centerQ, centerR);
        if (centerHex != null && centerHex.isExplored()) {
            centerHex.setInsideBorder(true);
        }

        int[][] directions = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
        for (int[] d : directions) {
            Hex neighbor = getHexAt(centerQ + d[0], centerR + d[1]);
            if (neighbor != null && neighbor.isExplored()) {
                neighbor.setInsideBorder(true);
            }
        }
    }

    // =========================================================
    // متدهای کمکی
    // =========================================================

    /**
     * محاسبه Unit Cap فعلی بر اساس تعداد Settlement‌های فعال.
     * مقدار پایه: ۱۰ یونیت. هر Settlement: +۵ یونیت.
     */
    public int getUnitCap() {
        int cap = 10;
        for (Hex h : hexes) {
            Building b = h.getBuilding();
            if (b != null && b.getType() == BuildingType.SETTLEMENT && !b.isDestroyed()) {
                cap += 5;
            }
        }
        return cap;
    }

    /**
     * محاسبه فاصله بین دو هکس با فرمول استاندارد Axial Coordinates.
     */
    public int getHexDistance(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2)
                + Math.abs(q1 + r1 - q2 - r2)
                + Math.abs(r1 - r2)) / 2;
    }

    private TerrainType getRandomTerrain() {
        TerrainType[] terrains = TerrainType.values();
        return terrains[random.nextInt(terrains.length)];
    }

    public int getAliveUnitsCount() {
        int count = 0;
        for (Unit u : units) {
            if (u.isAlive()) count++;
        }
        return count;
    }

    // =========================================================
    // Getters
    // =========================================================

    public List<Hex> getHexes() { return hexes; }
    public List<Unit> getUnits() { return units; }
    public TownHall getTownHall() { return townHall; }
    public int getCurrentTurn() { return currentTurn; }
    public boolean isStarving() { return isStarving; }
    public void setStarving(boolean starving) { this.isStarving = starving; }

    public Hex getHexAt(int q, int r) {
        for (Hex hex : hexes) {
            if (hex.getQ() == q && hex.getR() == r) return hex;
        }
        return null;
    }
}