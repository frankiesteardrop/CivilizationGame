package model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public class GameMap {
    private final Repository<Hex> hexes;
    private final Repository<Unit> units;
    private final int radius;
    private final Random random;
    private final TownHall townHall;
    private int currentTurn = 1;
    private boolean isStarving = false;

    public GameMap(int radius) {
        this.radius = radius;
        this.hexes = new Repository<>();
        this.units = new Repository<>();
        this.townHall = new TownHall(0, 0);
        this.random = new Random();

        generateMap();
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
                    continue;
                }

                TerrainType terrain = getRandomTerrain();
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
                }
                hexes.add(newHex);
            }
        }
        ensureStartingResources();
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
        java.util.List<Hex> availableCandidates = new java.util.ArrayList<>();

        for (Hex hex : hexes.getAll()) {
            int dist = getHexDistance(townHall.getQ(), townHall.getR(), hex.getQ(), hex.getR());
            if (dist > 0 && dist <= 2) {
                if (hex.getTerrainType() == TerrainType.FOREST) {
                    hasForestNear = true;
                    if (!hex.hasResource(ResourceType.WOOD)) {
                        hex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
                    }
                }
                if (hex.getTerrainType() != TerrainType.MOUNTAIN && hex.getTerrainType() != TerrainType.FOREST) {
                    availableCandidates.add(hex);
                }
            }
        }

        if (!hasForestNear && !availableCandidates.isEmpty()) {
            Hex targetHex = availableCandidates.get(random.nextInt(availableCandidates.size()));
            targetHex.setTerrainType(TerrainType.FOREST);
            targetHex.clearResourceCompletely(ResourceType.FOOD);
            targetHex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
        } else if (!hasForestNear) {
            Hex forceHex = getHexAt(townHall.getQ() + 1, townHall.getR());
            if (forceHex != null) {
                forceHex.setTerrainType(TerrainType.FOREST);
                forceHex.addResource(ResourceType.WOOD, GameConfig.SEED_FOREST_WOOD);
            }
        }
    }

    private void spawnInitialUnits() {
        units.add(new Explorer(0, 0));
        units.add(new Builder(0, 0));
        units.add(new Builder(0, 0));
        units.add(new Worker(0, 0));
        units.add(new Worker(0, 0));
    }

    public void incrementTurn() {
        currentTurn++;
    }

    public void removeDeadUnits() {
        boolean hasDeadUnits = false;
        for (Unit u : units.getAll()) {
            if (!u.isAlive()) {
                hasDeadUnits = true;
                break;
            }
        }
        units.removeIf(u -> !u.isAlive());

        if (hasDeadUnits) {
            updateFogOfWar();
        }
    }

    public void updateFogOfWar() {
        for (Hex hex : hexes.getAll()) {
            hex.setVisible(false);
        }

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
            for (Hex hex : hexes.getAll()) {
                if (getHexDistance(unit.getQ(), unit.getR(), hex.getQ(), hex.getR()) <= visionRadius) {
                    hex.setVisible(true);
                    hex.setExplored(true);
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

    public boolean isContiguousToBorder(int q, int r) {
        Hex centerHex = getHexAt(q, r);
        if (centerHex != null && centerHex.isInsideBorder()) {
            return true;
        }

        int[][] directions = {{1, 0}, {1, -1}, {0, -1}, {-1, 0}, {-1, 1}, {0, 1}};
        for (int[] d : directions) {
            Hex neighbor = getHexAt(q + d[0], r + d[1]);
            if (neighbor != null && neighbor.isInsideBorder()) {
                return true;
            }
        }
        return false;
    }

    public Hex findEmptySpawnHex(int startQ, int startR) {
        List<Hex> sortedHexes = new ArrayList<>(hexes.getAll());
        sortedHexes.sort(Comparator.comparingInt(h -> getHexDistance(startQ, startR, h.getQ(), h.getR())));

        for (Hex hex : sortedHexes) {
            if ((hex.isExplored() || hex.isVisible()) && !hasUnitAt(hex.getQ(), hex.getR())) {
                return hex;
            }
        }

        for (Hex hex : sortedHexes) {
            if (!hasUnitAt(hex.getQ(), hex.getR())) {
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
        return (Math.abs(q1 - q2)
                + Math.abs(q1 + r1 - q2 - r2)
                + Math.abs(r1 - r2)) / 2;
    }

    private TerrainType getRandomTerrain() {
        TerrainType[] terrains = TerrainType.values();
        return terrains[random.nextInt(terrains.length)];
    }

    public int getAliveUnitsCount() {
        return (int) units.stream().filter(Unit::isAlive).count();
    }

    public List<Hex> getHexes() { return hexes.getAll(); }
    public List<Unit> getUnits() { return units.getAll(); }
    public TownHall getTownHall() { return townHall; }
    public int getCurrentTurn() { return currentTurn; }
    public boolean isStarving() { return isStarving; }
    public void setStarving(boolean starving) { this.isStarving = starving; }

    public Hex getHexAt(int q, int r) {
        for (Hex hex : hexes.getAll()) {
            if (hex.getQ() == q && hex.getR() == r) return hex;
        }
        return null;
    }
}