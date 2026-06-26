package model;

import java.util.HashMap;
import java.util.Map;

public class Inventory {
    private final Map<ResourceType, Integer> resources;
    private int maxCapacity;

    public Inventory(int initialCapacity) {
        this.maxCapacity = initialCapacity;
        this.resources = new HashMap<>();

        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.NONE) {
                resources.put(type, 0);
            }
        }
    }

    public int getResourceAmount(ResourceType type) {
        return resources.getOrDefault(type, 0);
    }

    public int getMaxCapacity() { return maxCapacity; }

    public void setMaxCapacity(int maxCapacity) { this.maxCapacity = maxCapacity; }

    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;

        int current = resources.getOrDefault(type, 0);
        int updated = current + amount;

        if (updated > maxCapacity) {
            resources.put(type, maxCapacity);
        } else {
            resources.put(type, updated);
        }
        GameEventDispatcher.fireResourceChanged(type, resources.get(type));
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

    public void forceDecreaseResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        resources.put(type, Math.max(0, current - amount));
        GameEventDispatcher.fireResourceChanged(type, resources.get(type));
    }

    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE) return true;
        return resources.getOrDefault(type, 0) >= amount;
    }
}