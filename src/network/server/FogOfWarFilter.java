package network.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import model.*;

import java.util.HashSet;
import java.util.Set;

/**
 * Filters a serialized {@link GameMap} so that a specific player only receives
 * data about hexes within their vision radius (Fog of War).
 *
 * <p>Alliance vision sharing (B26): when {@code alliedPlayerIds} is non-empty,
 * allied players' units and buildings also contribute to the visible area,
 * giving allies a shared Fog of War as required by the spec.
 *
 * <p>Operating at the JSON-tree level avoids deep-copying the complex GameMap
 * object while producing a correctly filtered payload for each client.
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Hexes NOT in vision: {@code building} → null, {@code resources} → {},
 *       {@code isVisible} → false, {@code isInsideBorder} → false.
 *       {@code isExplored} is kept so the client can render explored-but-fogged terrain.</li>
 *   <li>Units on non-visible hexes: removed from the {@code units.items} array.</li>
 *   <li>Vision sources: all buildings and units with {@code ownerId} matching the player
 *       OR any of their allies.</li>
 * </ul>
 */
public class FogOfWarFilter {

    private final Gson gson;

    public FogOfWarFilter(Gson gson) {
        this.gson = gson;
    }

    /**
     * Returns a Fog-of-War-filtered JSON string of the master map for the given player.
     * Allied players' vision is merged in (B26).
     *
     * @param masterMap       the authoritative server-side game state
     * @param playerId        the player whose vision determines the filter
     * @param alliedPlayerIds set of playerIds currently allied with this player;
     *                        their vision is also revealed to this player
     * @return filtered JSON string safe to send to that player's client
     */
    public String filterForPlayer(GameMap masterMap,
                                  String playerId,
                                  Set<String> alliedPlayerIds) {
        Set<String> visibleKeys = computeVisibleHexKeys(masterMap, playerId, alliedPlayerIds);

        // Serialize the full map to a mutable JSON tree
        JsonObject mapJson = gson.toJsonTree(masterMap).getAsJsonObject();

        // ── Filter hexes ──────────────────────────────────────────────────────
        JsonObject hexesObj = mapJson.getAsJsonObject("hexes");
        if (hexesObj != null) {
            JsonArray hexItems = hexesObj.getAsJsonArray("items");
            if (hexItems != null) {
                for (JsonElement el : hexItems) {
                    if (!el.isJsonObject()) continue;
                    JsonObject hex = el.getAsJsonObject();
                    int q = hex.get("q").getAsInt();
                    int r = hex.get("r").getAsInt();

                    if (!visibleKeys.contains(q + "," + r)) {
                        // Hidden by fog — strip sensitive data
                        hex.add("building",       JsonNull.INSTANCE);
                        hex.add("resources",      new JsonObject());   // empty map
                        hex.addProperty("isVisible",      false);
                        hex.addProperty("isInsideBorder", false);
                        // isExplored kept so client shows grayed-out explored tiles
                    }
                }
            }
        }

        // ── Filter units ──────────────────────────────────────────────────────
        JsonObject unitsObj = mapJson.getAsJsonObject("units");
        if (unitsObj != null) {
            JsonArray unitItems = unitsObj.getAsJsonArray("items");
            if (unitItems != null) {
                JsonArray visibleUnits = new JsonArray();
                for (JsonElement el : unitItems) {
                    if (!el.isJsonObject()) continue;
                    JsonObject unit = el.getAsJsonObject();
                    int q = unit.get("q").getAsInt();
                    int r = unit.get("r").getAsInt();
                    if (visibleKeys.contains(q + "," + r)) {
                        visibleUnits.add(unit);
                    }
                }
                unitsObj.add("items", visibleUnits);
            }
        }

        return mapJson.toString();
    }

    // ─── Vision Computation ───────────────────────────────────────────────────

    /**
     * Computes the set of "q,r" hex keys visible to the given player,
     * including vision contributed by allied players (B26).
     */
    private Set<String> computeVisibleHexKeys(GameMap masterMap,
                                              String playerId,
                                              Set<String> alliedPlayerIds) {
        Set<String> visible = new HashSet<>();

        // Own vision
        addPlayerVision(masterMap, playerId, visible);

        // Allied players' vision — shared FoW per spec (B26)
        if (alliedPlayerIds != null) {
            for (String allyId : alliedPlayerIds) {
                addPlayerVision(masterMap, allyId, visible);
            }
        }

        return visible;
    }

    /**
     * Adds all hexes visible to a single player (identified by ownerId)
     * into the given result set.
     * Vision sources: owned buildings + owned alive units.
     */
    private void addPlayerVision(GameMap masterMap, String playerId, Set<String> result) {
        if (playerId == null) return;

        // Vision from buildings owned by this player
        for (Hex hex : masterMap.getHexes()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed() && playerId.equals(b.getOwnerId())) {
                addVisionCircle(masterMap, hex.getQ(), hex.getR(), b.getVisionRadius(), result);
            }
        }

        // Vision from units owned by this player
        for (Unit unit : masterMap.getUnits()) {
            if (unit.isAlive() && playerId.equals(unit.getOwnerId())) {
                addVisionCircle(masterMap, unit.getQ(), unit.getR(), unit.getVisionRadius(), result);
            }
        }
    }

    /** Adds all hexes within {@code radius} distance of (centerQ, centerR) to {@code result}. */
    private void addVisionCircle(GameMap map, int centerQ, int centerR,
                                 int radius, Set<String> result) {
        for (Hex hex : map.getHexes()) {
            if (map.getHexDistance(centerQ, centerR, hex.getQ(), hex.getR()) <= radius) {
                result.add(hex.getQ() + "," + hex.getR());
            }
        }
    }
}