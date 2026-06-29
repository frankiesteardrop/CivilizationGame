package model;

import java.util.EnumMap;
import java.util.Map;

/**
 * مدیریت ذخیره منابع Town Hall.
 *
 * اصلاح گام ۴: هر منبع ظرفیت جداگانه دارد (per-resource capacity).
 * طبق داک: ارتقای انبار «فضای ذخیره چوب، سنگ و آهن را افزایش می‌دهد» —
 * پس منطقی‌ترین پیاده‌سازی داشتن ظرفیت مجزا برای هر نوع منبع است.
 *
 * ظرفیت غذا جداگانه مدیریت می‌شود چون مزارع دارند.
 * ظرفیت چوب، سنگ و آهن با ارتقای انبار افزایش می‌یابند.
 */
public class Inventory {

    // ظرفیت پیش‌فرض هر منبع در ابتدای بازی
    private static final int DEFAULT_FOOD_CAPACITY  = 200;
    private static final int DEFAULT_WOOD_CAPACITY  = 200;
    private static final int DEFAULT_STONE_CAPACITY = 200;
    private static final int DEFAULT_IRON_CAPACITY  = 200;

    // ظرفیت پس از ارتقای انبار اول
    private static final int UPGRADE1_CAPACITY = 500;

    // ظرفیت پس از ارتقای انبار دوم
    private static final int UPGRADE2_CAPACITY = 1000;

    private final Map<ResourceType, Integer> resources;
    private final Map<ResourceType, Integer> capacities;

    public Inventory() {
        this.resources  = new EnumMap<>(ResourceType.class);
        this.capacities = new EnumMap<>(ResourceType.class);

        // مقداردهی اولیه همه منابع به صفر
        for (ResourceType type : ResourceType.values()) {
            if (type != ResourceType.NONE) {
                resources.put(type, 0);
            }
        }

        // تنظیم ظرفیت اولیه هر منبع به صورت جداگانه
        capacities.put(ResourceType.FOOD,  DEFAULT_FOOD_CAPACITY);
        capacities.put(ResourceType.WOOD,  DEFAULT_WOOD_CAPACITY);
        capacities.put(ResourceType.STONE, DEFAULT_STONE_CAPACITY);
        capacities.put(ResourceType.IRON,  DEFAULT_IRON_CAPACITY);
    }

    // =========================================================
    // افزودن منبع (با رعایت سقف ظرفیت آن منبع)
    // =========================================================

    public void addResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;

        int current  = resources.getOrDefault(type, 0);
        int capacity = capacities.getOrDefault(type, DEFAULT_WOOD_CAPACITY);
        int updated  = Math.min(current + amount, capacity);

        resources.put(type, updated);
        GameEventDispatcher.fireResourceChanged(type, updated);
    }

    // =========================================================
    // مصرف منبع
    // =========================================================

    /**
     * @return true اگر منابع کافی بود و مصرف انجام شد
     */
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

    /**
     * کاهش اجباری منبع (حتی اگر نتیجه صفر شود — هرگز منفی نمی‌شود).
     */
    public void forceDecreaseResource(ResourceType type, int amount) {
        if (type == ResourceType.NONE || amount <= 0) return;
        int current = resources.getOrDefault(type, 0);
        int updated = Math.max(0, current - amount);
        resources.put(type, updated);
        GameEventDispatcher.fireResourceChanged(type, updated);
    }

    // =========================================================
    // بررسی موجودی
    // =========================================================

    public boolean hasEnough(ResourceType type, int amount) {
        if (type == ResourceType.NONE) return true;
        return resources.getOrDefault(type, 0) >= amount;
    }

    // =========================================================
    // ارتقای ظرفیت انبار
    // =========================================================

    /**
     * ارتقای اول: ظرفیت چوب، سنگ و آهن به UPGRADE1 می‌رسد.
     * طبق داک: غذا در این ارتقا تأثیر نمی‌گیرد.
     */
    public void upgradeToLevel1() {
        capacities.put(ResourceType.WOOD,  UPGRADE1_CAPACITY);
        capacities.put(ResourceType.STONE, UPGRADE1_CAPACITY);
        capacities.put(ResourceType.IRON,  UPGRADE1_CAPACITY);
    }

    /**
     * ارتقای دوم: ظرفیت چوب، سنگ و آهن به UPGRADE2 می‌رسد.
     */
    public void upgradeToLevel2() {
        capacities.put(ResourceType.WOOD,  UPGRADE2_CAPACITY);
        capacities.put(ResourceType.STONE, UPGRADE2_CAPACITY);
        capacities.put(ResourceType.IRON,  UPGRADE2_CAPACITY);
    }

    // =========================================================
    // Getters
    // =========================================================

    public int getResourceAmount(ResourceType type) {
        return resources.getOrDefault(type, 0);
    }

    /**
     * ظرفیت مخصوص یک منبع خاص — برای نمایش در HUD.
     */
    public int getCapacity(ResourceType type) {
        return capacities.getOrDefault(type, DEFAULT_WOOD_CAPACITY);
    }

    /**
     * حداکثر ظرفیت بین همه منابع — برای سازگاری با کدهای قدیمی.
     * @deprecated از getCapacity(ResourceType) استفاده کنید.
     */
    @Deprecated
    public int getMaxCapacity() {
        return capacities.getOrDefault(ResourceType.WOOD, DEFAULT_WOOD_CAPACITY);
    }

    /**
     * @deprecated از upgradeToLevel1() یا upgradeToLevel2() استفاده کنید.
     */
    @Deprecated
    public void setMaxCapacity(int maxCapacity) {
        // برای سازگاری با کدهای قدیمی — نباید دیگر استفاده شود
        capacities.put(ResourceType.WOOD,  maxCapacity);
        capacities.put(ResourceType.STONE, maxCapacity);
        capacities.put(ResourceType.IRON,  maxCapacity);
    }
}