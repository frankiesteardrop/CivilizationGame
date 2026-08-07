package model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class GameMap {
    private final Repository<Hex> hexes;
    private final Map<String, Hex> hexMap;
    private final Repository<Unit> units;
    private final int radius;
    private final Random random;
    private final TownHall townHall;
    private int currentTurn = 1;
    private boolean isStarving = false;

    private static final int[][] DIRECTIONS = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new Repository<>();
        this.hexMap = new HashMap<>();
        this.units = new Repository<>();
        this.townHall = new TownHall(0, 0);
        this.random = new Random();

        generateMap();
        generateRivers();
        generateNeutralStructures();
        setupInitialTerritory();
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
                    centerHex.setBuilding(this.townHall);
                    hexes.add(centerHex);
                    hexMap.put(q + "," + r, centerHex);
                    continue;
                }

                boolean isNearCenter = getHexDistance(0, 0, q, r) <= 2;
                TerrainType terrain = getRandomTerrain(isNearCenter);

                Hex newHex = new Hex(q, r, terrain);

                switch (terrain) {
                    case FOREST:
                        newHex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
                        break;
                    case MOUNTAIN:
                        newHex.addResource(ResourceType.STONE, GameConfig.SEED_MOUNTAIN_STONE);
                        if (random.nextDouble() < GameConfig.CHANCE_MOUNTAIN_IRON) {
                            newHex.addResource(ResourceType.IRON, GameConfig.SEED_MOUNTAIN_IRON);
                        }
                        break;
                    case MEADOW:
                        if (random.nextDouble() < GameConfig.CHANCE_MEADOW_FOOD) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_MEADOW_FOOD);
                            newHex.setResourceSubtype(random.nextBoolean() ? ResourceSubtype.WHEAT : ResourceSubtype.RICE);
                        }
                        break;
                    case PLAINS:
                        if (random.nextDouble() < GameConfig.CHANCE_PLAINS_ANIMAL) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_PLAINS_FOOD);
                            newHex.setResourceSubtype(random.nextBoolean() ? ResourceSubtype.CATTLE : ResourceSubtype.SHEEP);
                        }
                        break;
                    case SEA:
                        if (random.nextDouble() < 0.4) {
                            newHex.addResource(ResourceType.FOOD, GameConfig.SEED_MEADOW_FOOD);
                            newHex.setResourceSubtype(ResourceSubtype.FISH);
                        }
                        break;
                }
                hexes.add(newHex);
                hexMap.put(q + "," + r, newHex);
            }
        }
        ensureStartingResources();
    }

    private void generateRivers() {
        for (Hex hex : hexes.getAll()) {
            if (random.nextDouble() < 0.1) {
                int dir = random.nextInt(6);
                Hex neighbor = getNeighbor(hex, dir);
                if (neighbor != null && hex.getTerrainType() != TerrainType.SEA && neighbor.getTerrainType() != TerrainType.SEA) {
                    hex.setRiver(dir, true);
                    neighbor.setRiver((dir + 3) % 6, true);
                }
            }
        }
    }

    private void generateNeutralStructures() {
        List<Hex> validFarHexes = new ArrayList<>();
        for (Hex hex : hexes.getAll()) {
            if (getHexDistance(0, 0, hex.getQ(), hex.getR()) >= 6 &&
                    hex.getTerrainType() != TerrainType.SEA &&
                    hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE &&
                    hex.getBuilding() == null) {
                validFarHexes.add(hex);
            }
        }

        int postsToSpawn = 3;
        int tribesToSpawn = 5;

        for (int i = 0; i < postsToSpawn + tribesToSpawn && !validFarHexes.isEmpty(); i++) {
            Hex target = validFarHexes.remove(random.nextInt(validFarHexes.size()));
            if (i < postsToSpawn) {
                target.setBuilding(BuildingFactory.createBuilding(BuildingType.TRADING_POST));
            } else {
                target.setBuilding(BuildingFactory.createBuilding(BuildingType.TRIBE_CAMP));
            }
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

    public Hex getNeighbor(Hex hex, int direction) {
        int dq = DIRECTIONS[direction][0];
        int dr = DIRECTIONS[direction][1];
        return getHexAt(hex.getQ() + dq, hex.getR() + dr);
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
        List<Hex> availableCandidates = new ArrayList<>();

        for (Hex hex : hexes.getAll()) {
            int dist = getHexDistance(townHall.getQ(), townHall.getR(), hex.getQ(), hex.getR());
            if (dist > 0 && dist <= 2) {
                if (hex.getTerrainType() == TerrainType.FOREST) {
                    hasForestNear = true;
                    if (!hex.hasResource(ResourceType.WOOD)) {
                        hex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
                    }
                }
                if (hex.getTerrainType() != TerrainType.MOUNTAIN && hex.getTerrainType() != TerrainType.FOREST && hex.getTerrainType() != TerrainType.SEA && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                    availableCandidates.add(hex);
                }
            }
        }

        if (!hasForestNear && !availableCandidates.isEmpty()) {
            Hex targetHex = availableCandidates.get(random.nextInt(availableCandidates.size()));
            targetHex.setTerrainType(TerrainType.FOREST);
            targetHex.clearResourceCompletely(ResourceType.FOOD);
            targetHex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
        }
    }

    private void spawnInitialUnits() {
        addUnit(new Explorer(0, 0));
        addUnit(new Builder(0, 0));
        addUnit(new Builder(0, 0));
        addUnit(new Worker(0, 0));
        addUnit(new Worker(0, 0));
    }

    public void addUnit(Unit unit) {
        if (unit != null) {
            units.add(unit);
            updateFogOfWar();
        }
    }

    public void incrementTurn() { currentTurn++; }

    public void removeDeadUnits() {
        boolean hasDeadUnits = false;
        for (Unit u : units.getAll()) {
            if (!u.isAlive()) { hasDeadUnits = true; break; }
        }
        units.removeIf(u -> !u.isAlive());
        if (hasDeadUnits) updateFogOfWar();
    }

    public void updateFogOfWar() {
        for (Hex hex : hexes.getAll()) { hex.setVisible(false); }

        for (Hex hex : hexes.getAll()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed()) {
                int bVision = b.getVisionRadius();
                for (Hex other : hexes.getAll()) {
                    if (getHexDistance(hex.getQ(), hex.getR(), other.getQ(), other.getR()) <= bVision) {
                        other.setVisible(true);
                        other.setExplored(true);
                    }
                }
            }
        }

        for (Unit unit : units.getAll()) {
            if (!unit.isAlive()) continue;
            int visionRadius = unit.getVisionRadius();
            boolean isExplorer = (unit instanceof Explorer);
            for (Hex hex : hexes.getAll()) {
                if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                    hex.setVisible(true);
                    if (isExplorer) hex.setExplored(true);
                }
            }
        }
    }

    public void expandBorderAt(int centerQ, int centerR) {
        Hex centerHex = getHexAt(centerQ, centerR);
        if (centerHex != null && centerHex.isExplored()) centerHex.setInsideBorder(true);

        for (int i = 0; i < 6; i++) {
            Hex neighbor = getNeighbor(centerHex, i);
            if (neighbor != null && neighbor.isExplored()) neighbor.setInsideBorder(true);
        }
    }

    public boolean isContiguousToBorder(int q, int r) {
        Hex centerHex = getHexAt(q, r);
        if (centerHex != null && centerHex.isInsideBorder()) return true;

        for (int i = 0; i < 6; i++) {
            Hex neighbor = getHexAt(q + DIRECTIONS[i][0], r + DIRECTIONS[i][1]);
            if (neighbor != null && neighbor.isInsideBorder()) return true;
        }
        return false;
    }

    public Hex findEmptySpawnHex(int startQ, int startR) {
        List<Hex> sortedHexes = new ArrayList<>(hexes.getAll());
        sortedHexes.sort(Comparator.comparingInt(h -> getHexDistance(startQ, startR, h.getQ(), h.getR())));

        for (Hex hex : sortedHexes) {
            if ((hex.isExplored() || hex.isVisible()) && !hasUnitAt(hex.getQ(), hex.getR()) && hex.getTerrainType() != TerrainType.SEA && hex.getTerrainType() != TerrainType.MOUNTAIN_RANGE) {
                return hex;
            }
        }
        return getHexAt(startQ, startR);
    }

    public boolean hasUnitAt(int q, int r) {
        return units.stream().anyMatch(u -> u.isAlive() && u.getQ() == q && u.getR() == r);
    }

    public int getUnitCap() {
        int cap = GameConfig.UNIT_CAP_BASE;
        for (Hex h : hexes.getAll()) {
            Building b = h.getBuilding();
            if (b != null && b.getType() == BuildingType.SETTLEMENT && !b.isDestroyed()) {
                cap += GameConfig.UNIT_CAP_SETTLEMENT_BONUS;
            }
        }
        return cap;
    }

    public int getHexDistance(int q1, int r1, int q2, int r2) {
        return (Math.abs(q1 - q2) + Math.abs(q1 + r1 - q2 - r2) + Math.abs(r1 - r2)) / 2;
    }

    public int getAliveUnitsCount() { return (int) units.stream().filter(Unit::isAlive).count(); }
    public List<Hex> getHexes() { return hexes.getAll(); }
    public List<Unit> getUnits() { return units.getAll(); }
    public TownHall getTownHall() { return townHall; }
    public int getCurrentTurn() { return currentTurn; }
    public boolean isStarving() { return isStarving; }
    public void setStarving(boolean starving) { this.isStarving = starving; }
    public Hex getHexAt(int q, int r) { return hexMap.get(q + "," + r); }
}