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
 * <p>Operating at the JSON-tree level avoids the need to deep-copy the complex
 * GameMap object while still producing a correctly filtered payload.
 *
 * <p>Filtering rules:
 * <ul>
 *   <li>Hexes NOT in vision: {@code building} → null, {@code resources} → empty,
 *       {@code isVisible} → false, {@code isInsideBorder} → false.</li>
 *   <li>Units on non-visible hexes: removed from the {@code units.items} array.</li>
 *   <li>Vision sources: all buildings and units whose {@code ownerId} matches
 *       the player, using their respective vision radii.</li>
 * </ul>
 */
public class FogOfWarFilter {

    private final Gson gson;

    public FogOfWarFilter(Gson gson) {
        this.gson = gson;
    }

    /**
     * Returns a filtered JSON string of the master map for the given player.
     * Allied players' vision is included (B13 shared Fog of War).
     *
     * @param masterMap       the authoritative server-side game state
     * @param playerId        the player whose vision determines the filter
     * @param alliedPlayerIds set of playerIds who are currently allied with playerId
     * @return filtered JSON string safe to send to that player's client
     */
    public String filterForPlayer(GameMap masterMap, String playerId, Set<String> alliedPlayerIds) {
        Set<String> visibleKeys = computeVisibleHexKeys(masterMap, playerId, alliedPlayerIds);

        JsonObject mapJson = gson.toJsonTree(masterMap).getAsJsonObject();

        // ── Filter hexes ──────────────────────────────────────────────────────────
        JsonObject hexesObj = mapJson.getAsJsonObject("hexes");
        if (hexesObj != null) {
            JsonArray hexItems = hexesObj.getAsJsonArray("items");
            if (hexItems != null) {
                for (JsonElement hexEl : hexItems) {
                    if (!hexEl.isJsonObject()) continue;
                    JsonObject hex = hexEl.getAsJsonObject();
                    int q = hex.get("q").getAsInt();
                    int r = hex.get("r").getAsInt();

                    if (!visibleKeys.contains(q + "," + r)) {
                        hex.add("building",       JsonNull.INSTANCE);
                        hex.add("resources",      new JsonObject());
                        hex.addProperty("isVisible",      false);
                        hex.addProperty("isInsideBorder", false);
                    }
                }
            }
        }

        // ── Filter units ──────────────────────────────────────────────────────────
        JsonObject unitsObj = mapJson.getAsJsonObject("units");
        if (unitsObj != null) {
            JsonArray unitItems = unitsObj.getAsJsonArray("items");
            if (unitItems != null) {
                JsonArray visibleUnits = new JsonArray();
                for (JsonElement unitEl : unitItems) {
                    if (!unitEl.isJsonObject()) continue;
                    JsonObject unit = unitEl.getAsJsonObject();
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

    // ─── Vision Computation ───────────────────────────────────────────────────────

    /**
     * Computes visible hex keys for the given player, including
     * vision from allied players' units and buildings (B13).
     */
    private Set<String> computeVisibleHexKeys(GameMap masterMap, String playerId,
                                              Set<String> alliedPlayerIds) {
        Set<String> visible = new HashSet<>();

        // Own vision
        addPlayerVision(masterMap, playerId, visible);

        // Allied players' vision (B13 — shared Fog of War)
        for (String allyId : alliedPlayerIds) {
            addPlayerVision(masterMap, allyId, visible);
        }

        return visible;
    }

    /** Adds all hexes visible to a single player (by ownerId) into the result set. */
    private void addPlayerVision(GameMap masterMap, String playerId, Set<String> result) {
        // Vision from owned buildings
        for (Hex hex : masterMap.getHexes()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed() && playerId.equals(b.getOwnerId())) {
                addVisionCircle(masterMap, hex.getQ(), hex.getR(), b.getVisionRadius(), result);
            }
        }

        // Vision from owned units
        for (Unit unit : masterMap.getUnits()) {
            if (unit.isAlive() && playerId.equals(unit.getOwnerId())) {
                addVisionCircle(masterMap, unit.getQ(), unit.getR(), unit.getVisionRadius(), result);
            }
        }
    }

    private void addVisionCircle(GameMap map, int centerQ, int centerR,
                                 int radius, Set<String> result) {
        for (Hex hex : map.getHexes()) {
            if (map.getHexDistance(centerQ, centerR, hex.getQ(), hex.getR()) <= radius) {
                result.add(hex.getQ() + "," + hex.getR());
            }
        }
    }
}