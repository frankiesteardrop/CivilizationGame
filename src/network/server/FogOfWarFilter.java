package network.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import model.*;

import java.util.HashSet;
import java.util.Set;

public class FogOfWarFilter {

    private final Gson gson;

    public FogOfWarFilter(Gson gson) {
        this.gson = gson;
    }

    public String filterForPlayer(GameMap masterMap,
                                  String playerId,
                                  Set<String> alliedPlayerIds) {
        Set<String> visibleKeys = computeVisibleHexKeys(masterMap, playerId, alliedPlayerIds);

        JsonObject mapJson = gson.toJsonTree(masterMap).getAsJsonObject();

        JsonObject hexesObj = mapJson.getAsJsonObject("hexes");
        if (hexesObj != null) {
            JsonArray hexItems = hexesObj.getAsJsonArray("items");
            if (hexItems != null) {
                for (JsonElement el : hexItems) {
                    if (!el.isJsonObject()) continue;
                    JsonObject hex = el.getAsJsonObject();
                    int q = hex.get("q").getAsInt();
                    int r = hex.get("r").getAsInt();
                    String key = q + "," + r;

                    Hex realHex = masterMap.getHexAt(q, r);
                    boolean isExploredByThisClient = (realHex != null && realHex.isExplored(playerId));

                    if (!isExploredByThisClient) {
                        hex.add("exploredBy", new JsonArray());
                    }

                    if (!visibleKeys.contains(key)) {
                        hex.add("building",       JsonNull.INSTANCE);
                        hex.add("resources",      new JsonObject());
                        hex.addProperty("isVisible",      false);
                        hex.addProperty("isInsideBorder", false);

                        JsonArray emptyWalls = new JsonArray();
                        JsonArray emptyWallHps = new JsonArray();
                        for (int i = 0; i < 6; i++) {
                            emptyWalls.add(false);
                            emptyWallHps.add(0);
                        }
                        hex.add("walls", emptyWalls);
                        hex.add("wallHp", emptyWallHps);
                    }
                }
            }
        }

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

    private Set<String> computeVisibleHexKeys(GameMap masterMap,
                                              String playerId,
                                              Set<String> alliedPlayerIds) {
        Set<String> visible = new HashSet<>();
        addPlayerVision(masterMap, playerId, visible);
        if (alliedPlayerIds != null) {
            for (String allyId : alliedPlayerIds) {
                addPlayerVision(masterMap, allyId, visible);
            }
        }
        return visible;
    }

    private void addPlayerVision(GameMap masterMap, String playerId, Set<String> result) {
        if (playerId == null) return;

        for (Hex hex : masterMap.getHexes()) {
            Building b = hex.getBuilding();
            if (b != null && !b.isDestroyed() && playerId.equals(b.getOwnerId())) {
                addVisionCircle(masterMap, hex.getQ(), hex.getR(), b.getVisionRadius(), result);
            }
        }

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