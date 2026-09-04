package model;

import java.util.*;
import java.util.function.Predicate;

public class GameMap {

    private final Repository<Hex> hexes;
    private final Map<String, Hex> hexMap;
    private final Repository<Unit> units;
    private final int radius;
    private Random random;
    private final TownHall townHall;
    private int currentTurn = 1;
    private boolean isStarving = false;

    private int bearCooldown = 0;

    private static final int[][] DIRECTIONS = {
            {1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}
    };

    // ─── Constructors ─────────────────────────────────────────────────────────

    /**
     * Single-player constructor — uses a randomly generated seed each time,
     * so the map layout differs between sessions.
     */
    public GameMap(int radius) {
        this(radius, new Random().nextLong());
    }

    /**
     * Pre-designed map constructor (B10) — uses a fixed seed so the same
     * terrain is generated every time. Used by {@link network.server.GameStateManager}
     * when initializing a multiplayer session from a {@link model.maps.MapDefinition}.
     *
     * @param radius     map radius (number of hex rings from center)
     * @param randomSeed fixed seed for reproducible procedural generation
     */
    public GameMap(int radius, long randomSeed) {
        this.radius   = radius;
        this.hexes    = new Repository<>();
        this.hexMap   = new HashMap<>();
        this.units    = new Repository<>();
        this.townHall = new TownHall(0, 0);
        this.random   = new Random(randomSeed);

        generateMap();
        generateRivers();
        ensureMapConnectivity();
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

    // ─── Multiplayer Setup Methods (B10) ─────────────────────────────────────

    /**
     * Places a player's starting Town Hall at the given hex coordinates and
     * spawns their initial units (Explorer, 2 Builders, 2 Workers) — all with
     * {@code ownerId} set to {@code playerId}.
     *
     * <p>Called by {@link network.server.GameStateManager#initializeGame()} for
     * each player after {@link #clearCenterSetup()} has been called.
     *
     * @param playerId the unique client ID of the player
     * @param spawnQ   axial Q coordinate of the spawn hex
     * @param spawnR   axial R coordinate of the spawn hex
     */
    public void placePlayerSpawn(String playerId, int spawnQ, int spawnR) {
        Hex spawnHex = getHexAt(spawnQ, spawnR);
        if (spawnHex == null) {
            // Fall back to nearest accessible hex if exact spawn is blocked
            spawnHex = findNearbyEmptyHex(spawnQ, spawnR, 3);
            if (spawnHex == null) {
                System.err.println("[GameMap] Could not find valid spawn hex for player: " + playerId);
                return;
            }
        }

        // Ensure terrain is passable (not sea or mountain range)
        if (spawnHex.getTerrainType() == TerrainType.SEA
                || spawnHex.getTerrainType() == TerrainType.MOUNTAIN_RANGE) {
            spawnHex.setTerrainType(TerrainType.PLAINS);
        }

        // Create and place the player's Town Hall (its constructor adds starting resources)
        TownHall playerTH = new TownHall(spawnHex.getQ(), spawnHex.getR());
        playerTH.setOwnerId(playerId);
        spawnHex.setBuilding(playerTH);

        // Establish initial territory (radius 1 around spawn)
        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(spawnHex.getQ(), spawnHex.getR(),
                    hex.getQ(), hex.getR()) <= 1) {
                hex.setInsideBorder(true);
                hex.setExplored(true);
            }
        }

        // Spawn initial units with ownerId set
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

    /**
     * Removes the default single-player setup (Town Hall at (0,0) plus initial
     * unowned units) so that multiplayer spawns can be placed cleanly.
     *
     * <p>Call this immediately after constructing the map and before any
     * {@link #placePlayerSpawn(String, int, int)} calls.
     */
    public void clearCenterSetup() {
        // Remove the default Town Hall building from (0,0)
        Hex center = getHexAt(0, 0);
        if (center != null) {
            center.setBuilding(null);
        }

        // Remove all units that have no ownerId (the single-player default units)
        units.removeIf(u -> u.getOwnerId() == null);

        // Reset initial territory marks around center
        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) <= 1) {
                hex.setInsideBorder(false);
                hex.setExplored(false);
            }
        }
    }

    // ─── B32 — Thread-safe unit removal API ───────────────────────────────────

    /**
     * Removes all units that match the given predicate by delegating to
     * {@link Repository#removeIf(Predicate)} — which operates on the underlying
     * mutable list, not the unmodifiable view returned by {@link #getUnits()}.
     *
     * <p>This avoids the {@link UnsupportedOperationException} that occurs when
     * callers mistakenly call {@code getUnits().removeIf(...)}.
     */
    public void removeUnitsWhere(Predicate<Unit> predicate) {
        units.removeIf(predicate);
    }

    // ─── Existing Methods (unchanged) ─────────────────────────────────────────

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
            // Use Repository.removeIf (safe — operates on underlying mutable list)
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
        int baseCap = switch (townHall.getLevel()) {
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
                ||  u.getType() == UnitType.CATAPULT)).count(); // B15: add CATAPULT
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
    public TownHall   getTownHall()    { return townHall; }
    public int        getCurrentTurn() { return currentTurn; }
    public boolean    isStarving()     { return isStarving; }
    public void       setStarving(boolean s) { this.isStarving = s; }
    public Hex        getHexAt(int q, int r) { return hexMap.get(q + "," + r); }
    public Random     getRandom()      { return random; }

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
}