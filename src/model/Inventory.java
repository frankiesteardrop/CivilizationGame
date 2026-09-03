package model;

import java.util.EnumMap;
import java.util.Map;

public class Inventory {

    private final Map<ResourceType, Integer> resources;
    private final Map<ResourceType, Integer> capacities;
    // فیلد جدید برای مسدودسازی (Lock) منابع هنگام ارسال پیشنهاد تجارت
    private final Map<ResourceType, Integer> lockedResources;

    public Inventory() {
        this.resources  = new EnumMap<>(ResourceType.class);
        this.capacities = new EnumMap<>(ResourceType.class);
        this.lockedResources = new EnumMap<>(ResourceType.class);

        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.NONE) {
                resources.put(type, 0);
                lockedResources.put(type, 0); // مقداردهی اولیه منابع قفل شده
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

        if (updated != current) {
            resources.put(type, updated);
            GameEventDispatcher.fireResourceChanged(type, updated);
        }
    }

    // اصلاح شده: استفاده از متد hasEnough تا منابع قفل شده را نتوان مصرف کرد
    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;

        if (hasEnough(type, amount)) {
            int current = resources.getOrDefault(type, 0);
            resources.put(type, current - amount);
            GameEventDispatcher.fireResourceChanged(type, resources.get(type));
            return true;
        }
        return false;
    }

    // متد جدید: مصرف منابعی که قبلاً برای یک ترید قفل شده بودند (هنگام Accept شدن ترید)
    public boolean consumeLockedResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;

        int currentLocked = lockedResources.getOrDefault(type, 0);
        int currentTotal = resources.getOrDefault(type, 0);

        if (currentLocked >= amount && currentTotal >= amount) {
            lockedResources.put(type, currentLocked - amount);
            resources.put(type, currentTotal - amount);
            GameEventDispatcher.fireResourceChanged(type, resources.get(type));
            return true;
        }
        return false;
    }

    // متد جدید: قفل کردن موقت منابع (هنگام ارسال پیشنهاد ترید)
    public void lockResource(ResourceType type, int amount) {
        if (type != ResourceType.NONE && hasEnough(type, amount)) {
            lockedResources.put(type, lockedResources.getOrDefault(type, 0) + amount);
        }
    }

    // متد جدید: آزادسازی منابع (هنگام Reject شدن یا Cancel شدن ترید)
    public void unlockResource(ResourceType type, int amount) {
        if (type != ResourceType.NONE) {
            int currentLocked = lockedResources.getOrDefault(type, 0);
            lockedResources.put(type, Math.max(0, currentLocked - amount));
        }
    }

    // اصلاح شده: مقدار در دسترس برابر است با کل منابع منهای منابع قفل شده
    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE) return true;
        int available = resources.getOrDefault(type, 0) - lockedResources.getOrDefault(type, 0);
        return available >= amount;
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