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

    private void generateMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {

                if (q == 0 && r == 0) {
                    Hex centerHex = new Hex(q, r, TerrainType.PLAINS);
                    centerHex.setExplored(true);
                    centerHex.setInsideBorder(true);
                    // اتصال تان‌هال به هکس مرکزی (از گام ۸)
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

        // [اصلاح حیاتی گام ۱۱]: تضمین وجود منابع اولیه (حداقل یک جنگل) در شعاع ۲ هکسی تان‌هال
        ensureStartingResources();
    }

    /**
     * متد کمکی برای تضمین دسترسی به منابع حیاتی اولیه (جنگل)
     */
    private void ensureStartingResources() {
        boolean hasForestNear = false;
        List<Hex> availableCandidates = new ArrayList<>();

        // اسکن هکس‌های با فاصله حداکثر ۲ از مبدأ
        for (Hex hex : hexes) {
            int dist = getHexDistance(0, 0, hex.getQ(), hex.getR());
            if (dist > 0 && dist <= 2) {
                if (hex.getTerrainType() == TerrainType.FOREST) {
                    hasForestNear = true;
                    break; // یک جنگل پیدا شد، نقشه معتبر است
                }
                // کاندیداها: هکس‌هایی که کوهستان نیستند تا ظاهر نقشه خیلی به هم نریزد
                if (hex.getTerrainType() != TerrainType.MOUNTAIN) {
                    availableCandidates.add(hex);
                }
            }
        }

        // اگر جنگلی در شعاع ۲ هکسی پیدا نشد، یک هکس کاندیدا را به زور به جنگل تبدیل می‌کنیم
        if (!hasForestNear && !availableCandidates.isEmpty()) {
            Hex targetHex = availableCandidates.get(random.nextInt(availableCandidates.size()));
            targetHex.setTerrainType(TerrainType.FOREST);

            // اگر قبلاً غذایی به این هکس (مثلاً دشت) اختصاص یافته بود، آن را پاک می‌کنیم
            if (targetHex.hasResource(ResourceType.FOOD)) {
                targetHex.extractResource(ResourceType.FOOD, 10000);
            }

            // اضافه کردن ۵۰۰ واحد چوب به هکسِ تغییریافته
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

    public void nextTurn() {
        for (Unit unit : units) {
            if (unit.isAlive()) {
                unit.resetAP();
            }
        }

        isStarving = EconomyManager.processEndTurn(this);

        if (isStarving) {
            for (Unit unit : units) {
                if (unit.isAlive()) {
                    unit.consumeAP(1);
                }
            }
        }

        units.removeIf(u -> !u.isAlive());
        currentTurn++;
    }

    public void updateFogOfWar() {
        Hex centerHex = getHexAt(townHall.getQ(), townHall.getR());
        if (centerHex != null) centerHex.setExplored(true);

        for (Hex hex : hexes) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 2) {
                hex.setExplored(true);
            }
        }

        for (Unit unit : units) {
            if (!unit.isAlive()) continue;
            int visionRadius = unit.getVisionRadius();
            for (Hex hex : hexes) {
                if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                    hex.setExplored(true);
                }
            }
        }

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