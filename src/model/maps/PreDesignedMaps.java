package model.maps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class PreDesignedMaps {

    private static final Map<String, MapDefinition> MAPS = new LinkedHashMap<>();

    static {
        MAPS.put("alpha", new MapDefinition(
                "alpha",
                "🗺 Divided Valleys (2P)",
                12,
                314159265L,
                Arrays.asList(
                        new int[]{-6, 0},   // Player 1: west
                        new int[]{ 6, 0}    // Player 2: east
                ),
                2
        ));

        MAPS.put("beta", new MapDefinition(
                "beta",
                "⚔️ Three Kingdoms (3P)",
                14,
                271828182L,
                Arrays.asList(
                        new int[]{ 0, -10}, // Player 1: north
                        new int[]{10,   0}, // Player 2: east
                        new int[]{-10, 10}  // Player 3: southwest  (equilateral triangle)
                ),
                3
        ));

        MAPS.put("gamma", new MapDefinition(
                "gamma",
                "🌍 Grand Conquest (4P)",
                16,
                161803398L,
                Arrays.asList(
                        new int[]{ 12,   0}, // Player 1: east
                        new int[]{  0,  12}, // Player 2: south
                        new int[]{-12,   0}, // Player 3: west
                        new int[]{  0, -12}  // Player 4: north
                ),
                4
        ));
    }

    private PreDesignedMaps() {}

    public static MapDefinition getMap(String id) {
        return MAPS.getOrDefault(id, MAPS.values().iterator().next());
    }

    public static List<MapDefinition> getAllMaps() {
        return Collections.unmodifiableList(new ArrayList<>(MAPS.values()));
    }

    public static String getDefaultMapId() {
        return "alpha";
    }
}