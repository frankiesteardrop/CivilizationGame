package model;

import java.util.EnumMap;
import java.util.Map;

public class Inventory {

    private final Map<ResourceType, Integer> resources;
    private final Map<ResourceType, Integer> capacities;

    public Inventory() {
        this.resources  = new EnumMap<>(ResourceType.class);
        this.capacities = new EnumMap<>(ResourceType.class);

        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.NONE) {
                resources.put(type, 0);
            }
        }

        capacities.put(ResourceType.FOOD,  GameConfig.DEFAULT_FOOD_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.DEFAULT_WOOD_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.DEFAULT_STONE_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.DEFAULT_IRON_CAPACITY);
    }

    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;

        int current  = resources.getOrDefault(type, 0);
        int capacity = capacities.getOrDefault(type, 0);
        int updated  = Math.min(current + amount, capacity);

        // رفع باگ 29: فقط اگر تغییری صورت گرفت، رویداد شلیک شود
        if (updated != current) {
            resources.put(type, updated);
            GameEventDispatcher.fireResourceChanged(type, updated);
        }
    }

    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;

        int current = resources.getOrDefault(type, 0);
        if (current >= amount) {
            resources.put(type, current - amount);
            GameEventDispatcher.fireResourceChanged(type, resources.get(type));
            return true;
        }
        return false;
    }

    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE) return true;
        return resources.getOrDefault(type, 0) >= amount;
    }

    public void upgradeToLevel2() {
        capacities.put(ResourceType.FOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.TH_UPGRADE2_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.TH_UPGRADE2_CAPACITY);
    }

    public void upgradeToLevel3() {
        capacities.put(ResourceType.FOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.TH_UPGRADE3_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.TH_UPGRADE3_CAPACITY);
    }

    public int getResourceAmount(ResourceType type) {
        return resources.getOrDefault(type, 0);
    }

    public int getCapacity(ResourceType type) {
        return capacities.getOrDefault(type, 0);
    }
}