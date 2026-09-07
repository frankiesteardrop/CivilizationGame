package model.maps;

import java.util.Collections;
import java.util.List;

public final class MapDefinition {

    private final String id;
    private final String displayName;
    private final int    radius;
    private final long   randomSeed;
    private final List<int[]> spawnPoints;
    private final int maxPlayers;

    public MapDefinition(String id, String displayName, int radius,
                         long randomSeed, List<int[]> spawnPoints, int maxPlayers) {
        this.id           = id;
        this.displayName  = displayName;
        this.radius       = radius;
        this.randomSeed   = randomSeed;
        this.spawnPoints  = Collections.unmodifiableList(spawnPoints);
        this.maxPlayers   = maxPlayers;
    }

    public String      getId()          { return id; }
    public String      getDisplayName() { return displayName; }
    public int         getRadius()      { return radius; }
    public long        getRandomSeed()  { return randomSeed; }
    public List<int[]> getSpawnPoints() { return spawnPoints; }
    public int         getMaxPlayers()  { return maxPlayers; }

    @Override
    public String toString() {
        return displayName + " (" + maxPlayers + "P)";
    }
}