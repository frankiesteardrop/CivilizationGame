package controller;

import com.google.gson.*;
import model.*;
import java.io.*;
import java.nio.file.Files;
import java.lang.reflect.Type;
import java.lang.reflect.Field;
import java.util.Base64;
import java.util.Random;

public class SaveLoadController {
    private final MainController mainController;
    private final Gson gson;
    private static final String SAVE_DIR = "saves/";

    public SaveLoadController(MainController mainController) {
        this.mainController = mainController;
        new File(SAVE_DIR).mkdirs();

        this.gson = new GsonBuilder()
                .registerTypeAdapter(Building.class, new BuildingAdapter())
                .registerTypeAdapter(Unit.class, new UnitAdapter())
                .registerTypeAdapter(ProductionCommand.class, new ProductionCommandAdapter(mainController))
                // ثبت آداپتور جدید برای سریالایز کردن دقیق هسته Random
                .registerTypeAdapter(Random.class, new RandomAdapter())
                .setPrettyPrinting()
                .create();
    }

    public boolean saveGame(String slot) {
        try {
            File tempFile = new File(SAVE_DIR + slot + ".tmp");
            File finalFile = new File(SAVE_DIR + slot + ".json");

            String json = gson.toJson(mainController.getGameMap());
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

    public GameMap loadGame(String slot) {
        try {
            File file = new File(SAVE_DIR + slot + ".json");
            if (!file.exists()) {
                GameEventDispatcher.fireNotification("Save file not found!");
                return null;
            }

            String json = Files.readString(file.toPath());
            GameMap loadedMap = gson.fromJson(json, GameMap.class);

            // آن خط مخرب new Random() از اینجا حذف شد! Gson حالا با کمک RandomAdapter وضعیت قبلی را دقیقاً لود می‌کند.

            TownHall th = loadedMap.getTownHall();
            Hex thHex = loadedMap.getHexAt(th.getQ(), th.getR());
            if (thHex != null) thHex.setBuilding(th);

            // رفع باگ 08: اتصال مجدد (Reconnection) رفرنس‌های کارگران به ساختمان‌های روی نقشه
            for (Unit unit : loadedMap.getUnits()) {
                if (unit instanceof Worker) {
                    Worker worker = (Worker) unit;
                    if (worker.isStationed()) {
                        Hex workerHex = loadedMap.getHexAt(worker.getQ(), worker.getR());
                        if (workerHex != null && workerHex.getBuilding() != null && !workerHex.getBuilding().isDestroyed()) {
                            try {
                                Field stationedBuildingField = Worker.class.getDeclaredField("stationedBuilding");
                                stationedBuildingField.setAccessible(true);
                                stationedBuildingField.set(worker, workerHex.getBuilding());
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        } else {
                            worker.eject();
                        }
                    }
                }
            }

            GameEventDispatcher.fireNotification("Game Loaded Successfully from: " + slot);
            return loadedMap;
        } catch (Exception e) {
            e.printStackTrace();
            GameEventDispatcher.fireNotification("Load Failed!");
            return null;
        }
    }

    public void autosave() {
        saveGame("autosave");
    }

    // ─── آداپتور اختصاصی برای حفظ State کلاس Random ─────────────────────────
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
                return new Random(); // Fallback در صورت خرابی دیتای ذخیره شده
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

    private static class ProductionCommandAdapter implements JsonSerializer<ProductionCommand>, JsonDeserializer<ProductionCommand> {
        private final MainController mc;
        public ProductionCommandAdapter(MainController mc) { this.mc = mc; }

        @Override
        public JsonElement serialize(ProductionCommand src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("name", src.getName());
            obj.addProperty("turnsRemaining", src.getTurnsRemaining());
            obj.addProperty("isPopulationTask", src.isPopulationTask());
            obj.addProperty("isCanceled", src.isCanceled());
            return obj;
        }

        @Override
        public ProductionCommand deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj = json.getAsJsonObject();
            String name = obj.get("name").getAsString();
            int turns = obj.get("turnsRemaining").getAsInt();
            boolean isPop = obj.get("isPopulationTask").getAsBoolean();
            boolean isCanceled = obj.has("isCanceled") && obj.get("isCanceled").getAsBoolean();

            ProductionCommand cmd = new ProductionCommand(name, turns, isPop) {
                @Override
                public void execute() {
                    GameMap map = mc.getGameMap();
                    TownHall th = map.getTownHall();
                    switch(name) {
                        case "Warehouse Upgrade": th.upgradeLevel(); break;
                        case "Upgrade to Settlement": th.upgradeLevel(); break;
                        case "Upgrade to Capital": th.upgradeLevel(); break;
                        case "Tech: Stone Mine": th.setStoneMineUnlocked(true); break;
                        case "Tech: Iron Mine": th.setIronMineUnlocked(true); break;
                        case "Tech: Steel Tools": th.setSteelToolsUnlocked(true); break;
                        case "Tech: Seafaring": th.setSeafaringUnlocked(true); break;
                        case "Tech: Defensive Arch": th.applyDefensiveArchitecture(); break;
                        default:
                            Hex spawnHex = map.findEmptySpawnHex(th.getQ(), th.getR());
                            int tq = spawnHex != null ? spawnHex.getQ() : th.getQ();
                            int tr = spawnHex != null ? spawnHex.getR() : th.getR();
                            try {
                                UnitType ut = UnitType.valueOf(name);
                                map.addUnit(UnitFactory.createUnit(ut, tq, tr));
                            } catch(Exception ignored) {}
                            break;
                    }
                }
            };
            if (isCanceled) cmd.cancel();

            try {
                Field tField = ProductionCommand.class.getDeclaredField("turnsRemaining");
                tField.setAccessible(true);
                tField.set(cmd, turns);
            } catch(Exception ignored) {}

            return cmd;
        }
    }
}