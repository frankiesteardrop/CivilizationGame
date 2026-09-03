package model.maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of the three pre-designed multiplayer maps required by the Phase 3 spec.
 *
 * <p>Each map uses a fixed random seed for reproducible terrain generation, plus
 * explicit spawn coordinates designed to give all players a fair start:
 *
 * <ul>
 *   <li><b>Alpha</b> — "Divided Valleys" (2P, radius 12).
 *       Spawns at opposite ends of the east-west axis: (-6, 0) and (6, 0).
 *       Pairwise distance: 12. Max separation for this map size.</li>
 *   <li><b>Beta</b> — "Three Kingdoms" (3P, radius 14).
 *       Spawns form a perfect equilateral triangle at distance 10 from center:
 *       (0, -10), (10, 0), (-10, 10). All pairwise distances: 20.</li>
 *   <li><b>Gamma</b> — "Grand Conquest" (4P, radius 16).
 *       Spawns at the four cardinal directions at distance 12 from center:
 *       (12, 0), (0, 12), (-12, 0), (0, -12). Adjacent pairs: 12, diagonal: 24.</li>
 * </ul>
 */
public final class PreDesignedMaps {

    /** All maps in display order. Backed by LinkedHashMap to preserve insertion order. */
    private static final Map<String, MapDefinition> MAPS = new LinkedHashMap<>();

    static {
        // ── Alpha — 2-player ──────────────────────────────────────────────────
        MAPS.put("alpha", new MapDefinition(
                "alpha",
                "🗺 Divided Valleys (2P)",
                12,
                314159265L,  // fixed seed → reproducible terrain
                Arrays.asList(
                        new int[]{-6, 0},   // Player 1: west
                        new int[]{ 6, 0}    // Player 2: east
                ),
                2
        ));

        // ── Beta — 3-player ───────────────────────────────────────────────────
        MAPS.put("beta", new MapDefinition(
                "beta",
                "⚔️ Three Kingdoms (3P)",
                14,
                271828182L,  // fixed seed
                Arrays.asList(
                        new int[]{ 0, -10}, // Player 1: north
                        new int[]{10,   0}, // Player 2: east
                        new int[]{-10, 10}  // Player 3: southwest  (equilateral triangle)
                ),
                3
        ));

        // ── Gamma — 4-player ──────────────────────────────────────────────────
        MAPS.put("gamma", new MapDefinition(
                "gamma",
                "🌍 Grand Conquest (4P)",
                16,
                161803398L,  // fixed seed (golden ratio inspired)
                Arrays.asList(
                        new int[]{ 12,   0}, // Player 1: east
                        new int[]{  0,  12}, // Player 2: south
                        new int[]{-12,   0}, // Player 3: west
                        new int[]{  0, -12}  // Player 4: north
                ),
                4
        ));
    }

    private PreDesignedMaps() {} // utility class — no instances

    // ─── API ──────────────────────────────────────────────────────────────────

    /**
     * Returns the {@link MapDefinition} for the given id, or the default map
     * (Alpha) if the id is not found.
     */
    public static MapDefinition getMap(String id) {
        return MAPS.getOrDefault(id, MAPS.values().iterator().next());
    }

    /** Returns all available maps in display order. */
    public static List<MapDefinition> getAllMaps() {
        return Collections.unmodifiableList(new ArrayList<>(MAPS.values()));
    }

    /** Returns the id of the default map (used when no selection has been made). */
    public static String getDefaultMapId() {
        return "alpha";
    }
}