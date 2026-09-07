package model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public class Inventory {

    private final EnumMap<ResourceType, Integer> resources  = new EnumMap<>(ResourceType.class);
    private final EnumMap<ResourceType, Integer> capacities = new EnumMap<>(ResourceType.class);

    private final EnumMap<ResourceType, Integer> locked = new EnumMap<>(ResourceType.class);


    private final Map<String, Integer> items = new HashMap<>();

    public Inventory() {
        for (ResourceType type : ResourceType.values()) {
            if (type == ResourceType.NONE) continue;
            resources.put(type, 0);
            locked.put(type, 0);
        }
        capacities.put(ResourceType.FOOD,  GameConfig.DEFAULT_FOOD_CAPACITY);
        capacities.put(ResourceType.WOOD,  GameConfig.DEFAULT_WOOD_CAPACITY);
        capacities.put(ResourceType.STONE, GameConfig.DEFAULT_STONE_CAPACITY);
        capacities.put(ResourceType.IRON,  GameConfig.DEFAULT_IRON_CAPACITY);
    }


    public void applyStartingResources() {
        addResource(ResourceType.FOOD,  GameConfig.STARTING_FOOD);
        addResource(ResourceType.WOOD,  GameConfig.STARTING_WOOD);
        addResource(ResourceType.STONE, GameConfig.STARTING_STONE);
        addResource(ResourceType.IRON,  GameConfig.STARTING_IRON);
    }


    public void upgradeToLevel2() {
        setCapacity(ResourceType.FOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.WOOD,  GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.STONE, GameConfig.TH_UPGRADE2_CAPACITY);
        setCapacity(ResourceType.IRON,  GameConfig.TH_UPGRADE2_CAPACITY);
    }

    public void upgradeToLevel3() {
        setCapacity(ResourceType.FOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.WOOD,  GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.STONE, GameConfig.TH_UPGRADE3_CAPACITY);
        setCapacity(ResourceType.IRON,  GameConfig.TH_UPGRADE3_CAPACITY);
    }


    public int getResourceAmount(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return resources.getOrDefault(type, 0);
    }

    public int getCapacity(ResourceType type) {
        if (type == ResourceType.NONE) return 0;
        return capacities.getOrDefault(type, 0);
    }

    public void setCapacity(ResourceType type, int newCapacity) {
        if (type == ResourceType.NONE || newCapacity < 0) return;
        capacities.put(type, newCapacity);
        int current = resources.getOrDefault(type, 0);
        if (current > newCapacity) resources.put(type, newCapacity);
    }

    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        int cap     = capacities.getOrDefault(type, 0);
        resources.put(type, Math.min(current + amount, cap));
    }

    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (!hasEnough(type, amount)) return false;
        resources.put(type, resources.getOrDefault(type, 0) - amount);
        return true;
    }


    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        return getUnlocked(type) >= amount;
    }

    private int getUnlocked(ResourceType type) {
        return Math.max(0, resources.getOrDefault(type, 0)
                - locked.getOrDefault(type, 0));
    }


    public boolean lockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;
        if (getUnlocked(type) < amount) return false;
        locked.merge(type, amount, Integer::sum);
        return true;
    }

    public void unlockResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        locked.put(type, Math.max(0, locked.getOrDefault(type, 0) - amount));
    }

    public void consumeLockedResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int toRemove = Math.min(locked.getOrDefault(type, 0), amount);
        locked.put(type, locked.getOrDefault(type, 0) - toRemove);
        resources.put(type, Math.max(0, resources.getOrDefault(type, 0) - toRemove));
    }

    public void addItem(String itemName, int quantity) {
        if (itemName == null || quantity <= 0) return;
        items.merge(itemName, quantity, Integer::sum);
    }

    public boolean consumeItem(String itemName) {
        if (itemName == null) return false;
        int qty = items.getOrDefault(itemName, 0);
        if (qty <= 0) return false;
        if (qty == 1) items.remove(itemName);
        else          items.put(itemName, qty - 1);
        return true;
    }

    public Map<String, Integer> getItems() {
        return Collections.unmodifiableMap(items);
    }

    public boolean hasItem(String itemName) {
        return items.getOrDefault(itemName, 0) > 0;
    }
}