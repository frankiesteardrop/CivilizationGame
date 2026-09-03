package model.maps;

import java.util.Collections;
import java.util.List;

/**
 * Immutable descriptor for a pre-designed multiplayer map.
 *
 * <p>Using a fixed {@code randomSeed} ensures the map is generated identically
 * every time, so all clients see the same terrain layout.
 *
 * <p>{@code spawnPoints} are axial hex coordinates [q, r] for each player's
 * starting Town Hall. They are designed to be roughly equidistant so no player
 * has an unfair geographic advantage.
 */
public final class MapDefinition {

    private final String id;
    private final String displayName;
    private final int    radius;
    private final long   randomSeed;
    private final List<int[]> spawnPoints;  // each entry: [q, r]
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