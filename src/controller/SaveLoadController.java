package controller;

import com.google.gson.*;
import model.*;
import model.mission.Mission;
import model.state.mission.*;
import model.state.tribe.*;

import java.io.*;
import java.nio.file.Files;
import java.lang.reflect.Type;
import java.util.Base64;
import java.util.Random;

public class SaveLoadController {
    private final MainController mainController;
    private static final String SAVE_DIR = "saves/";

    public SaveLoadController(MainController mainController) {
        this.mainController = mainController;
        new File(SAVE_DIR).mkdirs();
    }

    /**
     * اصلاح C1: متمرکز کردن ساخت Gson با تمام Adapterهای لازم.
     *
     * ترتیب ثبت adapter مهم است:
     * - TribeStateAdapter و MissionStateAdapter (interface) باید قبل از Building و Unit باشند
     *   تا وقتی context.serialize/deserialize درون BuildingAdapter صدا می‌شود، آن‌ها در دسترس باشند.
     * - registerTypeHierarchyAdapter برای interface types استفاده می‌شود
     * - registerTypeAdapter برای abstract class ها (Building, Unit) که خودشان subclass routing دارند
     */
    private static Gson createGson() {
        return new GsonBuilder()
                // اصلاح C1: Adapterهای جدید برای TribeState و MissionState
                .registerTypeHierarchyAdapter(TribeState.class,  new TribeStateAdapter())
                .registerTypeHierarchyAdapter(MissionState.class, new MissionStateAdapter())
                // Adapterهای موجود
                .registerTypeAdapter(Building.class,         new BuildingAdapter())
                .registerTypeAdapter(Unit.class,             new UnitAdapter())
                .registerTypeAdapter(ProductionCommand.class, new ProductionCommandAdapter())
                .registerTypeAdapter(Random.class,           new RandomAdapter())
                .setPrettyPrinting()
                .create();
    }

    public boolean saveGame(String slot) {
        try {
            File tempFile  = new File(SAVE_DIR + slot + ".tmp");
            File finalFile = new File(SAVE_DIR + slot + ".json");

            String json = createGson().toJson(mainController.getGameMap());
            Files.writeString(tempFile.toPath(), json);

            if (finalFile.exists()) finalFile.delete();
            tempFile.renameTo(finalFile);
            GameEventDispatcher.fireNotification("Game Saved Successfully in slot: " + slot);
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            GameEventDispatcher.fireNotification("Save Failed!");
            return false;
        }
    }

    /**
     * اصلاح C1: متد لود با بازسازی کامل وابستگی‌های transient.
     *
     * مراحل post-load:
     * 1. بازسازی reference مپ در ProductionCommandها
     * 2. بازسازی station کارگرها
     * 3. [جدید] بازسازی TribeState از روی relationship/isAllied (safety net)
     * 4. [جدید] بازسازی MissionGoal (که transient است) از TribeType
     */
    public static GameMap loadGameMap(String slot) {
        try {
            File file = new File(SAVE_DIR + slot + ".json");
            if (!file.exists()) {
                return null;
            }

            String json = Files.readString(file.toPath());
            GameMap loadedMap = createGson().fromJson(json, GameMap.class);

            TownHall th    = loadedMap.getTownHall();
            Hex      thHex = loadedMap.getHexAt(th.getQ(), th.getR());
            if (thHex != null) thHex.setBuilding(th);

            // بازسازی Context مپ در ProductionCommandها
            for (ProductionCommand cmd : th.getProductionQueue()) {
                if (cmd != null) cmd.setContextMap(loadedMap);
            }

            // بازسازی استقرار کارگرها
            for (Unit unit : loadedMap.getUnits()) {
                if (unit instanceof Worker) {
                    Worker worker = (Worker) unit;
                    if (worker.isStationed()) {
                        Hex workerHex = loadedMap.getHexAt(worker.getQ(), worker.getR());
                        if (workerHex != null && workerHex.getBuilding() != null && !workerHex.getBuilding().isDestroyed()) {
                            worker.restoreStation(workerHex.getBuilding());
                        } else {
                            worker.eject();
                        }
                    }
                }
            }

            // ─── اصلاح C1: بازسازی TribeState و MissionGoal برای تمام قبیله‌ها ─────────────
            for (Hex hex : loadedMap.getHexes()) {
                if (!(hex.getBuilding() instanceof TribeCamp camp)) continue;

                // اصلاح C1: بازسازی TribeState به عنوان safety net (TribeStateAdapter اصلی کار را انجام داده)
                camp.getTribe().postLoad();

                // اصلاح C1: بازسازی MissionGoal که transient است و Gson آن را serialize نکرده
                Mission m = camp.getTribe().getMission();
                if (m != null) {
                    m.setGoal(camp.getTribe().getType().getMissionGoal());
                }
            }

            return loadedMap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public GameMap loadGame(String slot) {
        GameMap map = loadGameMap(slot);
        if (map != null) {
            GameEventDispatcher.fireNotification("Game Loaded Successfully from: " + slot);
        } else {
            GameEventDispatcher.fireNotification("Load Failed! File not found.");
        }
        return map;
    }

    public void autosave() { saveGame("autosave"); }

    // ─── اصلاح C1: Adapter برای TribeState (interface) ────────────────────────────────────

    /**
     * TribeStateAdapter: فیلد state در Tribe (از نوع interface TribeState) را
     * به فرمت {"STATE_NAME": "Friendly"} و برعکس تبدیل می‌کند.
     *
     * چرا نیاز است: Gson نمی‌تواند interface types را بدون راهنما deserialize کند.
     * registerTypeHierarchyAdapter باعث می‌شود این adapter برای تمام implementorها (AlliedState، ...) اجرا شود.
     */
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

            String name = obj.get("STATE_NAME").getAsString();
            return switch (name) {
                case "Allied"     -> new AlliedState();
                case "Friendly"   -> new FriendlyState();
                case "Displeased" -> new DispleasedState();
                case "Enemy"      -> new EnemyState();
                default           -> new NeutralState();
            };
        }
    }

    // ─── اصلاح C1: Adapter برای MissionState (interface) ─────────────────────────────────

    /**
     * MissionStateAdapter: فیلد state در Mission (از نوع interface MissionState) را
     * به فرمت {"STATE_NAME": "Active"} و برعکس تبدیل می‌کند.
     *
     * CompletedFailedState نیاز به نگه‌داری finalState دارد ("Completed"، "Failed"، "Cancelled")
     * که همان STATE_NAME است.
     */
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

            String name = obj.get("STATE_NAME").getAsString();
            return switch (name) {
                case "Active"           -> new ActiveMissionState();
                case "Ready to Deliver" -> new ReadyMissionState();
                case "Completed"        -> new CompletedFailedState("Completed");
                case "Failed"           -> new CompletedFailedState("Failed");
                case "Cancelled"        -> new CompletedFailedState("Cancelled");
                default                 -> new AvailableState();
            };
        }
    }

    // ─── اصلاح C1: BuildingAdapter — استفاده از context به جای new Gson() ─────────────────

    /**
     * اصلاح کلیدی: تغییر از new Gson().toJsonTree(src) به context.serialize(src, src.getClass())
     * و از new Gson().fromJson(json, clazz) به context.deserialize(json, clazz).
     *
     * دلیل: new Gson() هیچ‌کدام از adapter های ثبت‌شده (TribeStateAdapter، MissionStateAdapter، ...) را
     * ندارد. استفاده از context تضمین می‌کند تمام adapter ها برای serialize/deserialize فیلدهای nested
     * (مثل TribeCamp.tribe.state) درست اعمال می‌شوند.
     *
     * نکته درباره recursion: چون registerTypeAdapter (نه hierarchy) استفاده شده،
     * context.serialize(tribeCampInstance, TribeCamp.class) دوباره این adapter را trigger نمی‌کند.
     */
    private static class BuildingAdapter implements JsonSerializer<Building>, JsonDeserializer<Building> {
        @Override
        public JsonElement serialize(Building src, Type typeOfSrc, JsonSerializationContext context) {
            // اصلاح C1: از context.serialize استفاده می‌کنیم تا TribeStateAdapter و MissionStateAdapter
            // برای فیلدهای nested (مثل TribeCamp.tribe.state) درست فراخوانی شوند
            JsonObject obj = context.serialize(src, src.getClass()).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }

        @Override
        public Building deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String typeName = obj.get("CLASS_TYPE").getAsString();
            BuildingType type = BuildingType.valueOf(typeName);
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
            };
            // اصلاح C1: از context.deserialize استفاده می‌کنیم
            return context.deserialize(json, clazz);
        }
    }

    // ─── اصلاح C1: UnitAdapter — استفاده از context به جای new Gson() ──────────────────────

    /**
     * همان دلیل BuildingAdapter: context.serialize/deserialize برای یکپارچگی با تمام adapter ها.
     */
    private static class UnitAdapter implements JsonSerializer<Unit>, JsonDeserializer<Unit> {
        @Override
        public JsonElement serialize(Unit src, Type typeOfSrc, JsonSerializationContext context) {
            // اصلاح C1: از context.serialize به جای new Gson().toJsonTree
            JsonObject obj = context.serialize(src, src.getClass()).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }

        @Override
        public Unit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String typeName = obj.get("CLASS_TYPE").getAsString();
            UnitType type = UnitType.valueOf(typeName);
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
            // اصلاح C1: از context.deserialize به جای new Gson().fromJson
            return context.deserialize(json, clazz);
        }
    }

    // ─── RandomAdapter (بدون تغییر) ──────────────────────────────────────────────────────

    private static class RandomAdapter implements JsonSerializer<Random>, JsonDeserializer<Random> {
        @Override
        public JsonElement serialize(Random src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ObjectOutputStream oos = new ObjectOutputStream(baos);
                oos.writeObject(src);
                oos.close();
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                JsonObject obj = new JsonObject();
                obj.addProperty("base64State", base64);
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
                String base64 = json.getAsJsonObject().get("base64State").getAsString();
                byte[] data = Base64.getDecoder().decode(base64);
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

    // ─── ProductionCommandAdapter (بدون تغییر) ───────────────────────────────────────────

    private static class ProductionCommandAdapter
            implements JsonSerializer<ProductionCommand>, JsonDeserializer<ProductionCommand> {

        public ProductionCommandAdapter() { }

        @Override
        public JsonElement serialize(ProductionCommand src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("commandType", src.getCommandType());
            obj.addProperty("name", src.getName());
            obj.addProperty("turnsRemaining", src.getTurnsRemaining());
            obj.addProperty("isPopulationTask", src.isPopulationTask());
            obj.addProperty("isCanceled", src.isCanceled());

            if (src instanceof ProductionCommand.TechCommand) {
                obj.addProperty("techId", ((ProductionCommand.TechCommand) src).getTechId());
            } else if (src instanceof ProductionCommand.UnitCommand) {
                obj.addProperty("unitType", ((ProductionCommand.UnitCommand) src).getUnitType().name());
            }
            return obj;
        }

        @Override
        public ProductionCommand deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
                throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String cmdType = obj.get("commandType").getAsString();
            String name    = obj.get("name").getAsString();
            int turns      = obj.get("turnsRemaining").getAsInt();

            ProductionCommand cmd = null;
            if ("TECH".equals(cmdType)) {
                cmd = new ProductionCommand.TechCommand(name, turns, obj.get("techId").getAsString());
            } else if ("UNIT".equals(cmdType)) {
                cmd = new ProductionCommand.UnitCommand(name, turns,
                        UnitType.valueOf(obj.get("unitType").getAsString()));
            } else if ("UPGRADE_TH".equals(cmdType)) {
                cmd = new ProductionCommand.UpgradeTHCommand(name, turns);
            }

            if (cmd != null) {
                if (obj.has("isCanceled") && obj.get("isCanceled").getAsBoolean()) cmd.cancel();
            }
            return cmd;
        }
    }
}