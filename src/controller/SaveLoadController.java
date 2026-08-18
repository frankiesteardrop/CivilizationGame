package controller;

import com.google.gson.*;
import model.*;
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

    // اصلاح گام سوم: متمرکز کردن ساخت Gson برای استفاده استاتیک
    private static Gson createGson() {
        return new GsonBuilder()
                .registerTypeAdapter(Building.class, new BuildingAdapter())
                .registerTypeAdapter(Unit.class, new UnitAdapter())
                .registerTypeAdapter(ProductionCommand.class, new ProductionCommandAdapter())
                .registerTypeAdapter(Random.class, new RandomAdapter())
                .setPrettyPrinting()
                .create();
    }

    public boolean saveGame(String slot) {
        try {
            File tempFile = new File(SAVE_DIR + slot + ".tmp");
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

    // اصلاح گام سوم: متد استاتیک برای لود کردن مپ از منوی اصلی بدون نیاز به کنترلر قدیمی
    public static GameMap loadGameMap(String slot) {
        try {
            File file = new File(SAVE_DIR + slot + ".json");
            if (!file.exists()) {
                return null;
            }

            String json = Files.readString(file.toPath());
            GameMap loadedMap = createGson().fromJson(json, GameMap.class);

            TownHall th = loadedMap.getTownHall();
            Hex thHex = loadedMap.getHexAt(th.getQ(), th.getR());
            if (thHex != null) thHex.setBuilding(th);

            // رفع باگ پارادوکس: تزریق مپ جدید مستقیماً به کامندهای لود شده
            for (ProductionCommand cmd : th.getProductionQueue()) {
                if (cmd != null) cmd.setContextMap(loadedMap);
            }

            // رفع باگ استقرار کارگرها
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
        public Random deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
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

    private static class BuildingAdapter implements JsonSerializer<Building>, JsonDeserializer<Building> {
        @Override
        public JsonElement serialize(Building src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new Gson().toJsonTree(src).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }
        @Override
        public Building deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String typeName = obj.get("CLASS_TYPE").getAsString();
            BuildingType type = BuildingType.valueOf(typeName);
            Class<? extends Building> clazz = switch(type) {
                case TOWN_HALL -> TownHall.class;
                case LUMBER_MILL -> LumberMill.class;
                case STONE_MINE -> StoneMine.class;
                case IRON_MINE -> IronMine.class;
                case FARM -> Farm.class;
                case STABLE -> Stable.class;
                case SETTLEMENT -> Settlement.class;
                case DOCK -> Dock.class;
                case MONUMENT -> Monument.class;
                case BAZAAR -> Bazaar.class;
                case TRADING_POST -> TradingPost.class;
                case TRIBE_CAMP -> TribeCamp.class;
            };
            return new Gson().fromJson(json, clazz);
        }
    }

    private static class UnitAdapter implements JsonSerializer<Unit>, JsonDeserializer<Unit> {
        @Override
        public JsonElement serialize(Unit src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new Gson().toJsonTree(src).getAsJsonObject();
            obj.addProperty("CLASS_TYPE", src.getType().name());
            return obj;
        }
        @Override
        public Unit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String typeName = obj.get("CLASS_TYPE").getAsString();
            UnitType type = UnitType.valueOf(typeName);
            Class<? extends Unit> clazz = switch(type) {
                case WORKER -> Worker.class;
                case BUILDER -> Builder.class;
                case EXPLORER -> Explorer.class;
                case BORDER_EXPANDER -> BorderExpander.class;
                case SWORDSMAN -> Swordsman.class;
                case ARCHER -> Archer.class;
                case CAVALRY -> Cavalry.class;
                case BEAR -> Bear.class;
            };
            return new Gson().fromJson(json, clazz);
        }
    }

    // آداپتور اصلاح شده: بدون وابستگی مخرب به MainController
    private static class ProductionCommandAdapter implements JsonSerializer<ProductionCommand>, JsonDeserializer<ProductionCommand> {
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
        public ProductionCommand deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String cmdType = obj.get("commandType").getAsString();
            String name = obj.get("name").getAsString();
            int turns = obj.get("turnsRemaining").getAsInt();

            ProductionCommand cmd = null;
            if ("TECH".equals(cmdType)) {
                cmd = new ProductionCommand.TechCommand(name, turns, obj.get("techId").getAsString());
            } else if ("UNIT".equals(cmdType)) {
                cmd = new ProductionCommand.UnitCommand(name, turns, UnitType.valueOf(obj.get("unitType").getAsString()));
            } else if ("UPGRADE_TH".equals(cmdType)) {
                cmd = new ProductionCommand.UpgradeTHCommand(name, turns);
            }

            if (cmd != null) {
                if (obj.has("isCanceled") && obj.get("isCanceled").getAsBoolean()) cmd.cancel();
                // تزریق وابستگی در اینجا انجام نمی‌شود و برون‌سپاری شده است به لودینگ اصلی
            }
            return cmd;
        }
    }
}