package model;

import java.util.HashMap;
import java.util.Map;

public class Inventory {
    private Map<ResourceType, Integer> resources;
    private int maxCapacity;

    public Inventory(int initialCapacity) {
        this.maxCapacity = initialCapacity;
        this.resources = new HashMap<>();

        // مقداردهی اولیه تمام منابع با صفر
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.NONE) {
                resources.put(type, 0);
            }
        }
    }

    // گرفتن موجودی یک منبع خاص
    public int getResourceAmount(ResourceType type) {
        return resources.getOrDefault(type, 0);
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    // متد اضافه کردن منبع با در نظر گرفتن سقف انبار
    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;

        int current = resources.getOrDefault(type, 0);
        int updated = current + amount;

        if (updated > maxCapacity) {
            resources.put(type, maxCapacity);
        } else {
            resources.put(type, updated);
        }
    }

    // متد مصرف کردن منابع (اگه موجودی کافی باشه کم می‌کنه و true میده)
    public boolean consumeResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return true;

        int current = resources.getOrDefault(type, 0);
        if (current >= amount) {
            resources.put(type, current - amount);
            return true;
        }
        return false; // منبع کافی نیست
    }

    // کم کردن اجباری منبع (برای مواقع بحران و قحطی که موجودی نباید منفی بشه و به صفر کلمپ می‌شه)
    public void forceDecreaseResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        resources.put(type, Math.max(0, current - amount));
    }

    // فقط چک کردن اینکه آیا به اندازه کافی از یک منبع داریم یا نه
    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE) return true;
        return resources.getOrDefault(type, 0) >= amount;
    }
}