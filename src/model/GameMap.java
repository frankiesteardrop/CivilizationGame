package model;

import java.util.*;
import java.util.function.Predicate;

public class GameMap {

    private final Repository<Hex> hexes;
    private final Map<String, Hex> hexMap;
    private final Repository<Unit> units;

    // فیلد جدید برای مدیریت متمرکز امپراتوری‌ها
    private final Map<String, Empire> empires;

    private final int radius;
    private Random random;
    private final TownHall townHall;
    private int currentTurn = 1;
    private boolean isStarving = false;

    private int bearCooldown = 0;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    public GameMap(int radius) {
        this(radius, new Random().nextLong());
    }

    public GameMap(int radius, long randomSeed) {
        this(radius, false); // مقداردهی اولیه استاب
        this.random   = new Random(randomSeed);

        generateMap();
        generateRivers();
        ensureMapConnectivity();
        generateTradingPosts();
        setupInitialTerritory();
        spawnInitialUnits();
        updateFogOfWar();
    }

    // کانستراکتور Private جدید برای تولید Stub (بدون Generate)
    private GameMap(int radius, boolean isStub) {
        this.radius   = radius;
        this.hexes    = new Repository<>();
        this.hexMap   = new HashMap<>();
        this.units    = new Repository<>();
        this.empires  = new HashMap<>();
        this.townHall = new TownHall(0, 0);
        this.random   = new Random();
    }

    // فکتوری متد برای ساخت نقشه خالی در کلاینت (استاب)
    public static GameMap createClientStub(int radius) {
        return new GameMap(radius, true);
    }

    // متد کلیدی: تزریق دیتای سرور به نقشه محلی کلاینت بدون تغییر Reference
    public void updateFromServerState(GameMap serverMap) {
        if (serverMap == null) return;

        this.hexes.clear();
        this.hexMap.clear();
        for (Hex h : serverMap.getHexes()) {
            this.hexes.add(h);
            this.hexMap.put(h.getQ() + "," + h.getR(), h);
        }

        this.units.clear();
        for (Unit u : serverMap.getUnits()) {
            this.units.add(u);
        }

        this.empires.clear();
        if (serverMap.getEmpires() != null) {
            this.empires.putAll(serverMap.getEmpires());
        }

        this.currentTurn = serverMap.getCurrentTurn();
        this.isStarving = serverMap.isStarving();
    }

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

    private void ensureMapConnectivity() {
        final int MIN_REACHABLE_NEAR_TH = 12;

        Set<String> visited = new HashSet<>();
        Queue<Hex>  bfsQueue = new LinkedList<>();

        Hex startHex = getHexAt(0, 0);
        if (startHex == null) return;

        bfsQueue.add(startHex);
        visited.add("0,0");

        while (!bfsQueue.isEmpty()) {
            Hex current = bfsQueue.poll();
            for (int i = 0; i < 6; i++) {
                Hex neighbor = getNeighbor(current, i);
                if (neighbor == null) continue;

                String key = neighbor.getQ() + "," + neighbor.getR();
                if (visited.contains(key)) continue;

                if (neighbor.getTerrainType() == TerrainType.SEA) continue;
                if (neighbor.getTerrainType() == TerrainType.MOUNTAIN_RANGE) continue;

                visited.add(key);
                bfsQueue.add(neighbor);
            }
        }

        long nearReachable = visited.stream().filter(k -> {
            String[] parts = k.split(",");
            try {
                int q = Integer.parseInt(parts[0]);
                int r = Integer.parseInt(parts[1]);
                return getHexDistance(0, 0, q, r) <= 4;
            } catch (NumberFormatException e) { return false; }
        }).count();

        if (nearReachable < MIN_REACHABLE_NEAR_TH) {
            int fixRadius = 3;
            while (nearReachable < MIN_REACHABLE_NEAR_TH && fixRadius <= radius) {
                for (Hex hex : hexes.getAll()) {
                    if (hex.getTerrainType() == TerrainType.MOUNTAIN_RANGE
                            && getHexDistance(0, 0, hex.getQ(), hex.getR()) <= fixRadius) {
                        hex.setTerrainType(TerrainType.PLAINS);
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
                hex.setExplored(null, true);
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

    public void placePlayerSpawn(String playerId, int spawnQ, int spawnR) {
        Hex spawnHex = getHexAt(spawnQ, spawnR);
        if (spawnHex == null) {
            spawnHex = findNearbyEmptyHex(spawnQ, spawnR, 3);
            if (spawnHex == null) {
                System.err.println("[GameMap] Could not find valid spawn hex for player: " + playerId);
                return;
            }
        }

        if (spawnHex.getTerrainType() == TerrainType.SEA
                || spawnHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) {
            spawnHex.setTerrainType(TerrainType.PLAINS);
        }

        Empire emp = empires.computeIfAbsent(playerId, Empire::new);

        TownHall playerTH = new TownHall(spawnHex.getQ(), spawnHex.getR());
        playerTH.setOwnerId(playerId);
        playerTH.setEmpire(emp);
        spawnHex.setBuilding(playerTH);

        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(spawnHex.getQ(), spawnHex.getR(),
                    hex.getQ(), hex.getR()) <= 1) {
                hex.setInsideBorder(true);
                hex.setExplored(playerId, true);
            }
        }

        Explorer explorer = new Explorer(spawnHex.getQ(), spawnHex.getR());
        explorer.setOwnerId(playerId);
        addUnit(explorer);

        Builder b1 = new Builder(spawnHex.getQ(), spawnHex.getR());
        b1.setOwnerId(playerId);
        addUnit(b1);

        Builder b2 = new Builder(spawnHex.getQ(), spawnHex.getR());
        b2.setOwnerId(playerId);
        addUnit(b2);

        Worker w1 = new Worker(spawnHex.getQ(), spawnHex.getR());
        w1.setOwnerId(playerId);
        addUnit(w1);

        Worker w2 = new Worker(spawnHex.getQ(), spawnHex.getR());
        w2.setOwnerId(playerId);
        addUnit(w2);

        updateFogOfWar();
    }

    public void clearCenterSetup() {
        Hex center = getHexAt(0, 0);
        if (center != null) {
            center.setBuilding(null);
        }

        units.removeIf(u -> u.getOwnerId() == null);

        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 1) {
                hex.setInsideBorder(false);
                hex.setExplored(null, false);
            }
        }
    }

    public void removeUnitsWhere(Predicate<Unit> predicate) {
        units.removeIf(predicate);
    }

    public void addUnit(Unit unit) {
        if (unit != null) {
            units.add(unit);
            updateFogOfWar();
        }
    }

    public void checkPlayerElimination(String playerId) {
        boolean hasActiveTH = false;
        for (Hex h : hexes.getAll()) {
            if (h.getBuilding() != null && h.getBuilding().getType() == BuildingType.TOWN_HALL
                    && !h.getBuilding().isDestroyed() && playerId.equals(h.getBuilding().getOwnerId())) {
                hasActiveTH = true;
                break;
            }
        }

        if (!hasActiveTH) {
            units.removeIf(u -> playerId.equals(u.getOwnerId()));
            for (Hex h : hexes.getAll()) {
                if (h.getBuilding() != null && playerId.equals(h.getBuilding().getOwnerId())) {
                    h.getBuilding().takeDamage(9999);
                    h.setBuilding(null);
                }
            }
            GameEventDispatcher.fireNotification("💀 Player " + playerId + " has been eliminated from the game!");
        }
    }

    public void removeDeadUnits() {
        boolean hadDead = units.stream().anyMatch(u -> !u.isAlive());
        units.removeIf(u -> !u.isAlive());
        if (hadDead) updateFogOfWar();
    }

    public void incrementTurn() { currentTurn++; }

    public void updateFogOfWar() {
        for (Hex hex : hexes.getAll()) hex.clearVisibility();

        Set<String> activePlayers = new HashSet<>(empires.keySet());
        activePlayers.add(null);

        for (String pId : activePlayers) {
            for (Hex hex : hexes.getAll()) {
                Building b = hex.getBuilding();
                if (b != null && !b.isDestroyed() && Objects.equals(b.getOwnerId(), pId)) {
                    for (Hex other : hexes.getAll()) {
                        if (getHexDistance(hex.getQ(), hex.getR(),
                                other.getQ(), other.getR()) <= b.getVisionRadius()) {
                            other.setVisible(pId, true);
                            other.setExplored(pId, true);
                        }
                    }
                }
            }

            for (Unit unit : units.getAll()) {
                if (!unit.isAlive() || !Objects.equals(unit.getOwnerId(), pId)) continue;
                boolean isExplorer = (unit instanceof Explorer);
                for (Hex hex : hexes.getAll()) {
                    if (getHexDistance(unit.getQ(), unit.getR(),
                            hex.getQ(), hex.getR()) <= unit.getVisionRadius()) {
                        hex.setVisible(pId, true);
                        if (isExplorer) hex.setExplored(pId, true);
                    }
                }
            }
        }

        for (Hex hex : hexes.getAll()) {
            if (!(hex.getBuilding() instanceof TribeCamp camp)) continue;
            if (camp.isDiscovered()) continue;

            for (Unit unit : units.getAll()) {
                if (!unit.isAlive()) continue;
                if (getHexDistance(unit.getQ(), unit.getR(),
                        hex.getQ(), hex.getR()) <= unit.getVisionRadius()) {
                    camp.setDiscovered(true);
                    break;
                }
            }
        }
    }

    public void expandBorderAt(int centerQ, int centerR) {
        Hex centerHex = getHexAt(centerQ, centerR);
        if (centerHex == null) return;

        if (centerHex.isExplored()
                && centerHex.getTerrainType() != TerrainType.SEA
                && centerHex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
            centerHex.setInsideBorder(true);
        }

        for (int i = 0; i < 6; i++) {
            Hex neighbor = getNeighbor(centerHex, i);
            if (neighbor != null && neighbor.isExplored()
                    && neighbor.getTerrainType() != TerrainType.SEA
                    && neighbor.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                neighbor.setInsideBorder(true);
            }
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

    public int getBearCooldown()          { return bearCooldown; }
    public void setBearCooldown(int turns) { this.bearCooldown = turns; }
    public void decrementBearCooldown()   { if (bearCooldown > 0) bearCooldown--; }

    public boolean hasUnitAt(int q, int r) {
        return units.stream().anyMatch(u -> u.isAlive() && u.getQ() == q && u.getR() == r);
    }

    public int getMilitaryUnitCap() {
        int baseCap = switch (getTownHall().getLevel()) {
            case 1  -> GameConfig.UNIT_CAP_TH_LEVEL_1;
            case 2  -> GameConfig.UNIT_CAP_TH_LEVEL_2;
            default -> GameConfig.UNIT_CAP_TH_LEVEL_3;
        };

        int settlementBonus = (int) hexes.stream()
                .filter(h -> h.getBuilding() != null
                        && h.getBuilding().getType() == BuildingType.SETTLEMENT
                        && !h.getBuilding().isDestroyed())
                .count() * 5;

        return baseCap + settlementBonus;
    }

    public long getMilitaryUnitCount() {
        return units.stream().filter(u -> u.isAlive()
                && (u.getType() == UnitType.SWORDSMAN
                ||  u.getType() == UnitType.ARCHER
                ||  u.getType() == UnitType.CAVALRY
                ||  u.getType() == UnitType.CATAPULT)).count();
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

    public List<Hex>  getHexes()       { return hexes.getAll(); }
    public List<Unit> getUnits()       { return units.getAll(); }
    public int        getCurrentTurn() { return currentTurn; }
    public boolean    isStarving()     { return isStarving; }
    public void       setStarving(boolean s) { this.isStarving = s; }
    public Hex        getHexAt(int q, int r) { return hexMap.get(q + "," + r); }
    public Random     getRandom()      { return random; }

    public Empire getEmpire(String playerId) { return empires.get(playerId); }
    public Map<String, Empire> getEmpires() { return empires; }

    public Hex getHexOfBuilding(Building building) {
        for (Hex h : hexes.getAll()) {
            if (h.getBuilding() == building) return h;
        }
        return null;
    }

    public Hex findNearbyEmptyHex(int centerQ, int centerR, int radius) {
        for (Hex h : hexes.getAll()) {
            int dist = getHexDistance(centerQ, centerR, h.getQ(), h.getR());
            if (dist > 0 && dist <= radius
                    && h.getTerrainType() != TerrainType.SEA
                    && h.getTerrainType() != TerrainType.MOUNTAIN_RANGE
                    && !hasUnitAt(h.getQ(), h.getR())
                    && (h.getBuilding() == null || h.getBuilding().isDestroyed())) {
                return h;
            }
        }
        return null;
    }

    public boolean isRoadConnectedToCamp(TribeCamp camp) {
        Hex campHex = getHexOfBuilding(camp);
        if (campHex == null) return false;

        Set<Hex>   visited = new HashSet<>();
        Queue<Hex> queue   = new LinkedList<>();

        for (int i = 0; i < 6; i++) {
            Hex n = getNeighbor(campHex, i);
            if (n != null && n.hasRoad()) {
                queue.add(n);
                visited.add(n);
            }
        }

        while (!queue.isEmpty()) {
            Hex current = queue.poll();
            Building b = current.getBuilding();
            if (b != null && !b.isDestroyed()
                    && !(b instanceof TribeCamp)
                    && !(b instanceof TradingPost)) {
                return true;
            }
            for (int i = 0; i < 6; i++) {
                Hex n = getNeighbor(current, i);
                if (n != null && n.hasRoad() && !visited.contains(n)) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        return false;
    }

    public boolean hasDockWithinRadius(TribeCamp camp, int radius) {
        Hex campHex = getHexOfBuilding(camp);
        if (campHex == null) return false;

        for (Hex h : hexes.getAll()) {
            if (getHexDistance(campHex.getQ(), campHex.getR(),
                    h.getQ(), h.getR()) <= radius) {
                Building b = h.getBuilding();
                if (b != null && b.getType() == BuildingType.DOCK && !b.isDestroyed()) {
                    return true;
                }
            }
        }
        return false;
    }

    private TownHall activeTownHall = null;

    public TownHall getTownHall() {
        return (activeTownHall != null) ? activeTownHall : townHall;
    }

    public void setActiveTownHall(TownHall th) {
        this.activeTownHall = th;
    }

    public void clearActiveTownHall() {
        this.activeTownHall = null;
    }

    public TownHall getPlayerTownHall(String playerId) {
        if (playerId == null) return townHall;
        for (Hex h : hexes.getAll()) {
            if (h.getBuilding() instanceof TownHall th
                    && playerId.equals(th.getOwnerId())
                    && !th.isDestroyed()) {
                return th;
            }
        }
        return townHall;
    }

    public Inventory getPlayerInventory(String playerId) {
        return getPlayerTownHall(playerId).getInventory();
    }

    public java.util.List<TownHall> getAllPlayerTownHalls() {
        java.util.List<TownHall> result = new java.util.ArrayList<>();
        for (Hex h : hexes.getAll()) {
            if (h.getBuilding() instanceof TownHall th
                    && !th.isDestroyed()
                    && th.getOwnerId() != null) {
                result.add(th);
            }
        }
        return result;
    }
}