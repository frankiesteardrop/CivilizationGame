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
     * Returns a JSON string that is the master map with fog applied for
     * the given player. The master map object is never mutated.
     *
     * @param masterMap the authoritative server-side game state
     * @param playerId  the player whose vision determines the filter
     * @return filtered JSON string safe to send to that player's client
     */
    public String filterForPlayer(GameMap masterMap, String playerId) {
        Set<String> visibleKeys = computeVisibleHexKeys(masterMap, playerId);

        // Serialize the full map to a mutable JSON tree
        JsonObject mapJson = gson.toJsonTree(masterMap).getAsJsonObject();

        // ── Filter hexes ──────────────────────────────────────────────────────
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
                        // Hidden by fog: strip sensitive data
                        hex.add("building", JsonNull.INSTANCE);
                        hex.add("resources", new JsonObject());   // empty map
                        hex.addProperty("isVisible",      false);
                        hex.addProperty("isInsideBorder", false);
                        // isExplored is kept so the client can show explored-but-fogged terrain
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

    // ─── Vision Computation ───────────────────────────────────────────────────

    /**
     * Computes the set of "q,r" hex keys visible to the given player.
     * Vision comes from all buildings and units owned by this player.
     */
    private Set<String> computeVisibleHexKeys(GameMap masterMap, String playerId) {
        Set<String> visible = new HashSet<>();

        // Vision from player-owned buildings (Town Hall, Outpost, etc.)
        for (Hex hex : masterMap.getHexes()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed() && playerId.equals(b.getOwnerId())) {
                addVisionCircle(masterMap, hex.getQ(), hex.getR(), b.getVisionRadius(), visible);
            }
        }

        // Vision from player-owned units
        for (Unit unit : masterMap.getUnits()) {
            if (unit.isAlive() && playerId.equals(unit.getOwnerId())) {
                addVisionCircle(masterMap, unit.getQ(), unit.getR(), unit.getVisionRadius(), visible);
            }
        }

        return visible;
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