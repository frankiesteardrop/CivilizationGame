package network.util;

import com.google.gson.*;
import model.*;

import java.lang.reflect.Type;
import java.util.Random;

public class SharedGsonFactory {

    public static Gson createCustomGson() {
        return new GsonBuilder()
                .registerTypeHierarchyAdapter(model.state.tribe.TribeState.class, new TribeStateAdapter())
                .registerTypeHierarchyAdapter(model.state.mission.MissionState.class, new MissionStateAdapter())
                .registerTypeAdapter(Building.class, new BuildingAdapter())
                .registerTypeAdapter(Unit.class, new UnitAdapter())
                .registerTypeAdapter(ProductionCommand.class, new ProductionCommandAdapter())
                .registerTypeAdapter(Random.class, new RandomAdapter())
                .create();
    }

    private static class TribeStateAdapter implements JsonSerializer<model.state.tribe.TribeState>, JsonDeserializer<model.state.tribe.TribeState> {
        @Override
        public JsonElement serialize(model.state.tribe.TribeState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("STATE_NAME", src.getName());
            return obj;
        }

        @Override
        public model.state.tribe.TribeState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) return new model.state.tribe.NeutralState();
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("STATE_NAME")) return new model.state.tribe.NeutralState();
            return switch (obj.get("STATE_NAME").getAsString()) {
                case "Allied"     -> new model.state.tribe.AlliedState();
                case "Friendly"   -> new model.state.tribe.FriendlyState();
                case "Displeased" -> new model.state.tribe.DispleasedState();
                case "Enemy"      -> new model.state.tribe.EnemyState();
                default           -> new model.state.tribe.NeutralState();
            };
        }
    }

    private static class MissionStateAdapter implements JsonSerializer<model.state.mission.MissionState>, JsonDeserializer<model.state.mission.MissionState> {
        @Override
        public JsonElement serialize(model.state.mission.MissionState src, Type typeOfSrc, JsonSerializationContext context) {
            JsonObject obj = new JsonObject();
            obj.addProperty("STATE_NAME", src.getDisplayName());
            return obj;
        }

        @Override
        public model.state.mission.MissionState deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) return new model.state.mission.AvailableState();
            JsonObject obj = json.getAsJsonObject();
            if (!obj.has("STATE_NAME")) return new model.state.mission.AvailableState();
            return switch (obj.get("STATE_NAME").getAsString()) {
                case "Active"           -> new model.state.mission.ActiveMissionState();
                case "Ready to Deliver" -> new model.state.mission.ReadyMissionState();
                case "Completed"        -> new model.state.mission.CompletedFailedState("Completed");
                case "Failed"           -> new model.state.mission.CompletedFailedState("Failed");
                case "Cancelled"        -> new model.state.mission.CompletedFailedState("Cancelled");
                default                 -> new model.state.mission.AvailableState();
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
        public Building deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
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
                case APOTHECARY   -> Apothecary.class;
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
        public Unit deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
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
                case CATAPULT        -> Catapult.class;
            };
            return context.deserialize(json, clazz);
        }
    }

    private static class RandomAdapter implements JsonSerializer<Random>, JsonDeserializer<Random> {
        @Override
        public JsonElement serialize(Random src, Type typeOfSrc, JsonSerializationContext context) {
            try {
                java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
                java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(baos);
                oos.writeObject(src);
                oos.close();
                JsonObject obj = new JsonObject();
                obj.addProperty("base64State", java.util.Base64.getEncoder().encodeToString(baos.toByteArray()));
                return obj;
            } catch (java.io.IOException e) {
                return new JsonObject();
            }
        }

        @Override
        public Random deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            try {
                byte[] data = java.util.Base64.getDecoder().decode(json.getAsJsonObject().get("base64State").getAsString());
                java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.ByteArrayInputStream(data));
                Random r = (Random) ois.readObject();
                ois.close();
                return r;
            } catch (Exception e) {
                throw new JsonParseException("Failed to deserialize random state: corrupted base64 data");
            }
        }
    }

    private static class ProductionCommandAdapter implements JsonSerializer<ProductionCommand>, JsonDeserializer<ProductionCommand> {
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
        public ProductionCommand deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            JsonObject obj     = json.getAsJsonObject();
            String cmdType     = obj.get("commandType").getAsString();
            String name        = obj.get("name").getAsString();
            int    turns       = obj.get("turnsRemaining").getAsInt();

            ProductionCommand cmd = null;
            if ("TECH".equals(cmdType)) {
                cmd = new ProductionCommand.TechCommand(name, turns, obj.get("techId").getAsString());
            } else if ("UNIT".equals(cmdType)) {
                cmd = new ProductionCommand.UnitCommand(name, turns, UnitType.valueOf(obj.get("unitType").getAsString()));
            } else if ("UPGRADE_TH".equals(cmdType)) {
                cmd = new ProductionCommand.UpgradeTHCommand(name, turns);
            }

            if (cmd != null && obj.has("isCanceled") && obj.get("isCanceled").getAsBoolean()) {
                cmd.cancel();
            }
            return cmd;
        }
    }
}