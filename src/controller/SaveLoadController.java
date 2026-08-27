package controller;

import com.google.gson.*;
import model.*;
import model.mission.Mission;
import model.state.mission.*;
import model.state.tribe.*;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Random;

public class SaveLoadController {

    private final MainController mainController;
    private static final String SAVE_DIR        = "saves/";
    private static final String SAVE_VERSION    = "2.0";
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public static class SaveMetadata {
        public boolean isEmpty    = true;
        public String  slotName   = "";
        public int     turnNumber = 0;
        public String  season     = "";
        public int     thLevel    = 1;
        public String  saveTime   = "";
        public String  saveVersion = "";
        public String  gameSummary = "";
    }

    private static class SaveWrapper {
        String  saveVersion;
        String  slotName;
        int     turnNumber;
        String  season;
        int     thLevel;
        String  saveTime;
        String  gameSummary;
        GameMap gameData;
    }

    public SaveLoadController(MainController mainController) {
        this.mainController = mainController;
        new File(SAVE_DIR).mkdirs();
    }

    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeHierarchyAdapter(TribeState.class,  new TribeStateAdapter())
                .registerTypeHierarchyAdapter(MissionState.class, new MissionStateAdapter())
                .registerTypeAdapter(Building.class,          new BuildingAdapter())
                .registerTypeAdapter(Unit.class,              new UnitAdapter())
                .registerTypeAdapter(ProductionCommand.class,  new ProductionCommandAdapter())
                .registerTypeAdapter(Random.class,            new RandomAdapter())
                .setPrettyPrinting()
                .create();
    }

    public boolean saveGame(String slot) {
        try {
            File tempFile  = new File(SAVE_DIR + slot + ".tmp");
            File finalFile = new File(SAVE_DIR + slot + ".json");

            GameMap map = mainController.getGameMap();
            SaveWrapper wrapper = new SaveWrapper();
            wrapper.saveVersion = SAVE_VERSION;
            wrapper.slotName    = slot;
            wrapper.turnNumber  = map.getCurrentTurn();
            wrapper.season      = map.getCurrentSeason().name();
            wrapper.thLevel     = map.getTownHall().getLevel();
            wrapper.saveTime    = LocalDateTime.now().format(TIME_FMT);
            wrapper.gameSummary = buildGameSummary(map);
            wrapper.gameData    = map;

            String json = createGson().toJson(wrapper);
            Files.writeString(tempFile.toPath(), json);

            Files.move(tempFile.toPath(), finalFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);

            GameEventDispatcher.fireNotification("✅ Game saved to " + slot);
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            GameEventDispatcher.fireNotification("❌ Save failed: " + e.getMessage());
            return false;
        }
    }

    private String buildGameSummary(GameMap map) {
        long buildings = map.getHexes().stream()
                .filter(h -> h.getBuilding() != null && !h.getBuilding().isDestroyed()
                        && h.getBuilding().getType() != BuildingType.TOWN_HALL
                        && h.getBuilding().getType() != BuildingType.TRIBE_CAMP
                        && h.getBuilding().getType() != BuildingType.TRADING_POST)
                .count();
        return String.format("Buildings: %d | Military: %d | TH Lv%d | Happiness: %+d",
                buildings, map.getMilitaryUnitCount(),
                map.getTownHall().getLevel(),
                map.getTownHall().getHappiness());
    }

    public static GameMap loadGameMap(String slot) {
        try {
            File file = new File(SAVE_DIR + slot + ".json");
            if (!file.exists()) return null;

            String json = Files.readString(file.toPath());
            Gson gson = createGson();

            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            GameMap loadedMap;
            if (root.has("gameData") && !root.get("gameData").isJsonNull()) {
                JsonElement gameDataElement = root.get("gameData");
                loadedMap = gson.fromJson(gameDataElement, GameMap.class);
            } else {
                loadedMap = gson.fromJson(json, GameMap.class);
            }

            if (loadedMap == null) return null;

            TownHall th    = loadedMap.getTownHall();
            Hex      thHex = loadedMap.getHexAt(th.getQ(), th.getR());
            if (thHex != null) thHex.setBuilding(th);

            for (ProductionCommand cmd : th.getProductionQueue()) {
                if (cmd != null) cmd.setContextMap(loadedMap);
            }

            for (Unit unit : loadedMap.getUnits()) {
                if (unit instanceof Worker worker) {
                    if (worker.isStationed()) {
                        Hex workerHex = loadedMap.getHexAt(worker.getQ(), worker.getR());
                        if (workerHex != null && workerHex.getBuilding() != null
                                && !workerHex.getBuilding().isDestroyed()) {
                            worker.restoreStation(workerHex.getBuilding());
                        } else {
                            worker.eject();
                        }
                    }
                }
            }

            for (Hex hex : loadedMap.getHexes()) {
                if (!(hex.getBuilding() instanceof TribeCamp camp)) continue;
                camp.getTribe().postLoad();
                Mission m = camp.getTribe().getMission();
                if (m != null) {
                    m.setGoal(camp.getTribe().getType().getMissionGoal());
                    if (!camp.isDiscovered() && hex.isExplored()) {
                        camp.setDiscovered(true);
                    }
                }
            }

            for (Unit unit : loadedMap.getUnits()) {
                if (unit.getOwnerTribe() != null) {
                    TribeType type = unit.getOwnerTribe().getType();
                    for (Hex hex : loadedMap.getHexes()) {
                        if (hex.getBuilding() instanceof TribeCamp camp && camp.getTribe().getType() == type) {
                            unit.setOwnerTribe(camp.getTribe());
                            break;
                        }
                    }
                }
            }

            return loadedMap;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static SaveMetadata readSlotMetadata(String slot) {
        SaveMetadata meta = new SaveMetadata();
        meta.slotName = slot;

        try {
            File file = new File(SAVE_DIR + slot + ".json");
            if (!file.exists()) return meta;

            String json = Files.readString(file.toPath());
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();

            if (root.has("gameData")) {
                meta.saveVersion = root.has("saveVersion") ? root.get("saveVersion").getAsString() : "?";
                meta.turnNumber  = root.has("turnNumber")  ? root.get("turnNumber").getAsInt()     : 0;
                meta.season      = root.has("season")      ? root.get("season").getAsString()      : "?";
                meta.thLevel     = root.has("thLevel")     ? root.get("thLevel").getAsInt()        : 1;
                meta.saveTime    = root.has("saveTime")    ? root.get("saveTime").getAsString()    : "?";
                meta.gameSummary = root.has("gameSummary") ? root.get("gameSummary").getAsString() : "";
                meta.isEmpty     = false;
            } else {
                meta.saveVersion = "1.x";
                meta.season      = "Legacy";
                meta.saveTime    = "Old Format";
                meta.gameSummary = "Legacy save file";
                meta.isEmpty     = false;
                if (root.has("currentTurn")) {
                    meta.turnNumber = root.get("currentTurn").getAsInt();
                }
            }
        } catch (Exception e) {
            meta.isEmpty = true;
        }

        return meta;
    }

    public GameMap loadGame(String slot) {
        GameMap map = loadGameMap(slot);
        if (map != null) {
            GameEventDispatcher.fireNotification("📂 Game loaded from: " + slot);
        } else {
            GameEventDispatcher.fireNotification("❌ Load failed — file not found or corrupted.");
        }
        return map;
    }

    public void autosave() { saveGame("autosave"); }

    private static class TribeStateAdapter implements JsonSerializer<TribeState>, JsonDeserializer<TribeState> {
        @Override
        public JsonElement serialize(TribeState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("STATE_NAME", src.getName());
            return obj;
        }
        @Override
        public TribeState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) return new NeutralState();
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("STATE_NAME")) return new NeutralState();
            return switch (obj.get("STATE_NAME").getAsString()) {
                case "Allied"     -> new AlliedState();
                case "Friendly"   -> new FriendlyState();
                case "Displeased" -> new DispleasedState();
                case "Enemy"      -> new EnemyState();
                default           -> new NeutralState();
            };
        }
    }

    private static class MissionStateAdapter implements JsonSerializer<MissionState>, JsonDeserializer<MissionState> {
        @Override
        public JsonElement serialize(MissionState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("STATE_NAME", src.getDisplayName());
            return obj;
        }
        @Override
        public MissionState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            if (json == null || json.isJsonNull()) return new AvailableState();
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("STATE_NAME")) return new AvailableState();
            return switch (obj.get("STATE_NAME").getAsString()) {
                case "Active"           -> new ActiveMissionState();
                case "Ready to Deliver" -> new ReadyMissionState();
                case "Completed"        -> new CompletedFailedState("Completed");
                case "Failed"           -> new CompletedFailedState("Failed");
                case "Cancelled"        -> new CompletedFailedState("Cancelled");
                default                 -> new AvailableState();
            };
        }
    }

    private static class BuildingAdapter implements JsonSerializer<Building>, JsonDeserializer<Building> {
        @Override
        public JsonElement serialize(Building src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = context.serialize(src, src.getClass()).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }
        @Override
        public Building deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            BuildingType type = BuildingType.valueOf(obj.get("CLASS_TYPE").getAsString());

            Class<? extends Building> clazz = switch (type) {
                case TOWN_HALL    -> TownHall.class;
                case LUMBER_MILL  -> LumberMill.class;
                case STONE_MINE   -> StoneMine.class;
                case IRON_MINE    -> IronMine.class;
                case FARM         -> Farm.class;
                case STABLE       -> Stable.class;
                case SETTLEMENT   -> Settlement.class;
                case DOCK         -> Dock.class;
                case MONUMENT     -> Monument.class;
                case BAZAAR       -> Bazaar.class;
                case TRADING_POST -> TradingPost.class;
                case TRIBE_CAMP   -> TribeCamp.class;
                case OUTPOST      -> Outpost.class;
            };
            return context.deserialize(json, clazz);
        }
    }

    private static class UnitAdapter implements JsonSerializer<Unit>, JsonDeserializer<Unit> {
        @Override
        public JsonElement serialize(Unit src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = context.serialize(src, src.getClass()).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }
        @Override
        public Unit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            UnitType type = UnitType.valueOf(obj.get("CLASS_TYPE").getAsString());
            Class<? extends Unit> clazz = switch (type) {
                case WORKER          -> Worker.class;
                case BUILDER         -> Builder.class;
                case EXPLORER        -> Explorer.class;
                case BORDER_EXPANDER -> BorderExpander.class;
                case SWORDSMAN       -> Swordsman.class;
                case ARCHER          -> Archer.class;
                case CAVALRY         -> Cavalry.class;
                case BEAR            -> Bear.class;
            };
            return context.deserialize(json, clazz);
        }
    }

    private static class RandomAdapter implements JsonSerializer<Random>, JsonDeserializer<Random> {
        @Override
        public JsonElement serialize(Random src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(src);
                oos.close();
                JsonObject obj = new JsonObject();
                obj.addProperty("base64State", Base64.getEncoder().encodeToString(baos.toByteArray()));
                return obj;
            } catch (IOException e) {
                e.printStackTrace();
                return new JsonObject();
            }
        }
        @Override
        public Random deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            try {
                byte[] data = Base64.getDecoder()
                        .decode(json.getAsJsonObject().get("base64State").getAsString());
                ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
                Random random = (Random) ois.readObject();
                ois.close();
                return random;
            } catch (Exception e) {
                e.printStackTrace();
                return new Random();
            }
        }
    }

    private static class ProductionCommandAdapter
            implements JsonSerializer<ProductionCommand>, JsonDeserializer<ProductionCommand> {
        @Override
        public JsonElement serialize(ProductionCommand src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("commandType",     src.getCommandType());
            obj.addProperty("name",            src.getName());
            obj.addProperty("turnsRemaining",  src.getTurnsRemaining());
            obj.addProperty("isPopulationTask",src.isPopulationTask());
            obj.addProperty("isCanceled",      src.isCanceled());
            if (src instanceof ProductionCommand.TechCommand tc) {
                obj.addProperty("techId", tc.getTechId());
            } else if (src instanceof ProductionCommand.UnitCommand uc) {
                obj.addProperty("unitType", uc.getUnitType().name());
            }
            return obj;
        }
        @Override
        public ProductionCommand deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj  = json.getAsJsonObject();
            String cmdType  = obj.get("commandType").getAsString();
            String name     = obj.get("name").getAsString();
            int    turns    = obj.get("turnsRemaining").getAsInt();
            ProductionCommand cmd = null;
            if      ("TECH".equals(cmdType))       cmd = new ProductionCommand.TechCommand(name, turns, obj.get("techId").getAsString());
            else if ("UNIT".equals(cmdType))       cmd = new ProductionCommand.UnitCommand(name, turns, UnitType.valueOf(obj.get("unitType").getAsString()));
            else if ("UPGRADE_TH".equals(cmdType)) cmd = new ProductionCommand.UpgradeTHCommand(name, turns);
            if (cmd != null && obj.has("isCanceled") && obj.get("isCanceled").getAsBoolean()) cmd.cancel();
            return cmd;
        }
    }
}