package model;

import java.util.*;

public class GameMap {

    private final Repository<Hex> hexes;
    private final Map<String, Hex> hexMap;
    private final Repository<Unit> units;
    private final int radius;
    private final Random random;
    private final TownHall townHall;
    private int currentTurn = 1;
    private boolean isStarving = false;

    // F-33: cooldown خرس به GameMap منتقل شد (نه DisasterController)
    // چون DisasterController هر ترن از صفر ساخته می‌شود.
    private int bearCooldown = 0;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    public GameMap(int radius) {
        this.radius   = radius;
        this.hexes    = new Repository<>();
        this.hexMap   = new HashMap<>();
        this.units    = new Repository<>();
        this.townHall = new TownHall(0, 0);
        this.random   = new Random();

        generateMap();
        generateRivers();
        // F-34: بررسی connectivity بعد از generate نقشه
        ensureMapConnectivity();
        // F-38: فقط Trading Post — TribeCamp توسط TribeController spawn می‌شود
        generateTradingPosts();
        setupInitialTerritory();
        spawnInitialUnits();
        updateFogOfWar();
    }

    // ─── Map Generation ───────────────────────────────────────────────────────

    private void generateMap() {
        for (int q = -radius; q <= radius; q++) {
            int r1 = Math.max(-radius, -q - radius);
            int r2 = Math.min(radius, -q + radius);
            for (int r = r1; r <= r2; r++) {

                if (q == 0 && r == 0) {
                    Hex centerHex = new Hex(q, r, TerrainType.PLAINS);
                    centerHex.setBuilding(this.townHall);
                    hexes.add(centerHex);
                    hexMap.put(q + "," + r, centerHex);
                    continue;
                }

                boolean isNearCenter = (getHexDistance(0, 0, q, r) <= 2);
                TerrainType terrain  = getRandomTerrain(isNearCenter);
                Hex newHex           = new Hex(q, r, terrain);

                switch (terrain) {
                    case FOREST -> newHex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
                    case MOUNTAIN -> {
                        newHex.addResource(ResourceType.STONE, GameConfig.SEED_MOUNTAIN_STONE);
                        if (random.nextDouble() < GameConfig.CHANCE_MOUNTAIN_IRON)
                            newHex.addResource(ResourceType.IRON, GameConfig.SEED_MOUNTAIN_IRON);
                    }
                    case MEADOW -> {
                        if (random.nextDouble() < GameConfig.CHANCE_MEADOW_FOOD) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_MEADOW_FOOD);
                            newHex.setResourceSubtype(random.nextBoolean()
                                    ? ResourceSubtype.WHEAT : ResourceSubtype.RICE);
                        }
                    }
                    case PLAINS -> {
                        if (random.nextDouble() < GameConfig.CHANCE_PLAINS_ANIMAL) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_PLAINS_FOOD);
                            newHex.setResourceSubtype(random.nextBoolean()
                                    ? ResourceSubtype.CATTLE : ResourceSubtype.SHEEP);
                        }
                    }
                    case SEA -> {
                        if (random.nextDouble() < 0.4) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_MEADOW_FOOD);
                            newHex.setResourceSubtype(ResourceSubtype.FISH);
                        }
                    }
                }

                hexes.add(newHex);
                hexMap.put(q + "," + r, newHex);
            }
        }
        ensureStartingResources();
    }

    /**
     * F-34: اطمینان از اینکه TownHall در محدوده بسته‌ای قرار نگرفته است.
     *
     * الگوریتم BFS از موقعیت TH (0,0):
     * 1. همه هکس‌های قابل دسترس (نه SEA، نه MOUNTAIN_RANGE) را پیدا می‌کند
     * 2. هکس‌های قابل دسترس در شعاع ۴ از TH را می‌شمارد
     * 3. اگر کمتر از MIN_REACHABLE_NEAR_TH بود، MOUNTAIN_RANGE های مسدودکننده به PLAINS تبدیل می‌شوند
     *
     * طبق spec: "الگوریتم تولید نقشه باید مطمئن شود که TH در منطقه‌ای محصورشده قرار نمی‌گیرد."
     */
    private void ensureMapConnectivity() {
        final int MIN_REACHABLE_NEAR_TH = 12; // حداقل ۱۲ هکس قابل دسترس در شعاع ۴

        Set<String> visited = new HashSet<>();
        Queue<Hex>  bfsQueue = new LinkedList<>();

        Hex startHex = getHexAt(0, 0);
        if (startHex == null) return;

        bfsQueue.add(startHex);
        visited.add("0,0");

        // BFS — عبور از هکس‌های قابل عبور
        while (!bfsQueue.isEmpty()) {
            Hex current = bfsQueue.poll();
            for (int i = 0; i < 6; i++) {
                Hex neighbor = getNeighbor(current, i);
                if (neighbor == null) continue;

                String key = neighbor.getQ() + "," + neighbor.getR();
                if (visited.contains(key)) continue;

                // SEA و MOUNTAIN_RANGE غیرقابل عبور هستند
                if (neighbor.getTerrainType() == TerrainType.SEA) continue;
                if (neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                visited.add(key);
                bfsQueue.add(neighbor);
            }
        }

        // شمارش هکس‌های قابل دسترس در شعاع ۴ از TH
        long nearReachable = visited.stream().filter(k -> {
            String[] parts = k.split(",");
            try {
                int q = Integer.parseInt(parts[0]);
                int r = Integer.parseInt(parts[1]);
                return getHexDistance(0, 0, q, r) <= 4;
            } catch (NumberFormatException e) { return false; }
        }).count();

        // اگر کمتر از حداقل هکس قابل دسترس وجود داشت، MOUNTAIN_RANGE های نزدیک را باز کن
        if (nearReachable < MIN_REACHABLE_NEAR_TH) {
            int fixRadius = 3; // ابتدا شعاع ۳
            while (nearReachable < MIN_REACHABLE_NEAR_TH && fixRadius <= radius) {
                for (Hex hex : hexes.getAll()) {
                    if (hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE
                            && getHexDistance(0, 0, hex.getQ(), hex.getR()) <= fixRadius) {
                        hex.setTerrainType(TerrainType.PLAINS);
                        // آمار به‌روزرسانی — شمارش مجدد نیاز نیست، فرض می‌کنیم هر هکس باز شده کمک می‌کند
                        nearReachable++;
                        if (nearReachable >= MIN_REACHABLE_NEAR_TH) break;
                    }
                }
                fixRadius++;
            }
        }
    }

    private void generateRivers() {
        for (Hex hex : hexes.getAll()) {
            if (random.nextDouble() < 0.1) {
                int dir      = random.nextInt(6);
                Hex neighbor = getNeighbor(hex, dir);
                if (neighbor != null
                        && hex.getTerrainType()      != TerrainType.SEA
                        && neighbor.getTerrainType() != TerrainType.SEA) {
                    hex.setRiver(dir, true);
                    neighbor.setRiver((dir + 3) % 6, true);
                }
            }
        }
    }

    private void generateTradingPosts() {
        List<Hex> validFarHexes = new ArrayList<>();
        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) >= 6
                    && hex.getTerrainType() != TerrainType.SEA
                    && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                    && hex.getBuilding() == null) {
                validFarHexes.add(hex);
            }
        }

        int postsToSpawn = Math.min(3, validFarHexes.size());
        for (int i = 0; i < postsToSpawn; i++) {
            Hex target = validFarHexes.remove(random.nextInt(validFarHexes.size()));
            target.setBuilding(BuildingFactory.createBuilding(BuildingType.TRADING_POST));
        }
    }

    private TerrainType getRandomTerrain(boolean isNearCenter) {
        TerrainType[] terrains = TerrainType.values();
        TerrainType t = terrains[random.nextInt(terrains.length)];
        if (isNearCenter && (t == TerrainType.SEA || t == TerrainType.MOUNTAIN_RANGE)) {
            return TerrainType.PLAINS;
        }
        return t;
    }

    private void setupInitialTerritory() {
        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 1) {
                hex.setInsideBorder(true);
                hex.setExplored(true);
            }
        }
    }

    private void ensureStartingResources() {
        boolean hasForestNear = false;
        List<Hex> candidates  = new ArrayList<>();

        for (Hex hex : hexes.getAll()) {
            int dist = getHexDistance(townHall.getQ(), townHall.getR(),
                    hex.getQ(), hex.getR());
            if (dist > 0 && dist <= 2) {
                if (hex.getTerrainType() == TerrainType.FOREST) {
                    hasForestNear = true;
                    if (!hex.hasResource(ResourceType.WOOD))
                        hex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
                }
                if (hex.getTerrainType() != TerrainType.MOUNTAIN
                        && hex.getTerrainType() != TerrainType.FOREST
                        && hex.getTerrainType() != TerrainType.SEA
                        && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                    candidates.add(hex);
                }
            }
        }

        if (!hasForestNear && !candidates.isEmpty()) {
            Hex target = candidates.get(random.nextInt(candidates.size()));
            target.setTerrainType(TerrainType.FOREST);
            target.clearResourceCompletely(ResourceType.FOOD);
            target.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
        }
    }

    private void spawnInitialUnits() {
        addUnit(new Explorer(0, 0));
        addUnit(new Builder(0, 0));
        addUnit(new Builder(0, 0));
        addUnit(new Worker(0, 0));
        addUnit(new Worker(0, 0));
    }

    // ─── Unit management ─────────────────────────────────────────────────────

    public void addUnit(Unit unit) {
        if (unit != null) {
            units.add(unit);
            updateFogOfWar();
        }
    }

    public void removeDeadUnits() {
        boolean hadDead = units.stream().anyMatch(u -> !u.isAlive());
        units.removeIf(u -> !u.isAlive());
        if (hadDead) updateFogOfWar();
    }

    public void incrementTurn() { currentTurn++; }

    // ─── Fog of War ───────────────────────────────────────────────────────────

    public void updateFogOfWar() {
        for (Hex hex : hexes.getAll()) hex.setVisible(false);

        for (Hex hex : hexes.getAll()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed()) {
                for (Hex other : hexes.getAll()) {
                    if (getHexDistance(hex.getQ(), hex.getR(),
                            other.getQ(), other.getR()) <= b.getVisionRadius()) {
                        other.setVisible(true);
                        other.setExplored(true);
                    }
                }
            }
        }

        for (Unit unit : units.getAll()) {
            if (!unit.isAlive()) continue;
            boolean isExplorer = (unit instanceof Explorer);
            for (Hex hex : hexes.getAll()) {
                if (getHexDistance(unit.getQ(), unit.getR(),
                        hex.getQ(), hex.getR()) <= unit.getVisionRadius()) {
                    hex.setVisible(true);
                    if (isExplorer) hex.setExplored(true);
                }
            }
        }
    }

    // ─── Border ──────────────────────────────────────────────────────────────

    public void expandBorderAt(int centerQ, int centerR) {
        Hex centerHex = getHexAt(centerQ, centerR);
        if (centerHex != null && centerHex.isExplored()) centerHex.setInsideBorder(true);
        if (centerHex == null) return;
        for (int i = 0; i < 6; i++) {
            Hex neighbor = getNeighbor(centerHex, i);
            if (neighbor != null && neighbor.isExplored()) neighbor.setInsideBorder(true);
        }
    }

    public boolean isContiguousToBorder(int q, int r) {
        Hex centerHex = getHexAt(q, r);
        if (centerHex != null && centerHex.isInsideBorder()) return true;
        for (int i = 0; i < 6; i++) {
            Hex n = getHexAt(q + DIRECTIONS[i][0], r + DIRECTIONS[i][1]);
            if (n != null && n.isInsideBorder()) return true;
        }
        return false;
    }

    public Hex findEmptySpawnHex(int startQ, int startR) {
        List<Hex> sorted = new ArrayList<>(hexes.getAll());
        sorted.sort(Comparator.comparingInt(h ->
                getHexDistance(startQ, startR, h.getQ(), h.getR())));
        for (Hex hex : sorted) {
            if ((hex.isExplored() || hex.isVisible())
                    && !hasUnitAt(hex.getQ(), hex.getR())
                    && hex.getTerrainType() != TerrainType.SEA
                    && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                return hex;
            }
        }
        return getHexAt(startQ, startR);
    }

    // ─── Bear Cooldown (F-33) ─────────────────────────────────────────────────

    /** مقدار فعلی cooldown خرس. */
    public int getBearCooldown() { return bearCooldown; }

    /** تنظیم cooldown بعد از spawn خرس. */
    public void setBearCooldown(int turns) { this.bearCooldown = turns; }

    /** کاهش ۱ واحد cooldown در هر ترن (از TurnController). */
    public void decrementBearCooldown() { if (bearCooldown > 0) bearCooldown--; }

    // ─── Queries ─────────────────────────────────────────────────────────────

    public boolean hasUnitAt(int q, int r) {
        return units.stream().anyMatch(u -> u.isAlive() && u.getQ() == q && u.getR() == r);
    }

    public int getMilitaryUnitCap() {
        return switch (townHall.getLevel()) {
            case 1  -> GameConfig.UNIT_CAP_TH_LEVEL_1;
            case 2  -> GameConfig.UNIT_CAP_TH_LEVEL_2;
            default -> GameConfig.UNIT_CAP_TH_LEVEL_3;
        };
    }

    public long getMilitaryUnitCount() {
        return units.stream().filter(u -> u.isAlive()
                && (u.getType() == UnitType.SWORDSMAN
                || u.getType() == UnitType.ARCHER
                || u.getType() == UnitType.CAVALRY)).count();
    }

    public int getHexDistance(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2)
                + Math.abs(q1 + r1 - q2 - r2)
                + Math.abs(r1 - r2)) / 2;
    }

    public Hex getNeighbor(Hex hex, int direction) {
        if (hex == null || direction < 0 || direction > 5) return null;
        int dq = DIRECTIONS[direction][0];
        int dr = DIRECTIONS[direction][1];
        return getHexAt(hex.getQ() + dq, hex.getR() + dr);
    }

    public int getAliveUnitsCount() {
        return (int) units.stream().filter(Unit::isAlive).count();
    }

    public Season getCurrentSeason() {
        int seasonIndex = ((currentTurn - 1) / 10) % 4;
        return Season.values()[seasonIndex];
    }

    // ─── Getters ─────────────────────────────────────────────────────────────

    public List<Hex>  getHexes()       { return hexes.getAll(); }
    public List<Unit> getUnits()       { return units.getAll(); }
    public TownHall   getTownHall()    { return townHall; }
    public int        getCurrentTurn() { return currentTurn; }
    public boolean    isStarving()     { return isStarving; }
    public void       setStarving(boolean s) { this.isStarving = s; }
    public Hex        getHexAt(int q, int r) { return hexMap.get(q + "," + r); }
}