package model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class BuildingFactory {

    private static final Map<BuildingType, Supplier<Building>> registry = new HashMap<>();

    static {
        registry.put(BuildingType.LUMBER_MILL, LumberMill::new);
        registry.put(BuildingType.STONE_MINE, StoneMine::new);
        registry.put(BuildingType.IRON_MINE, IronMine::new);
        registry.put(BuildingType.FARM, Farm::new);
        registry.put(BuildingType.STABLE, Stable::new);
        registry.put(BuildingType.SETTLEMENT, Settlement::new);
        registry.put(BuildingType.DOCK, Dock::new);
        registry.put(BuildingType.MONUMENT, Monument::new);
        // ساختمان‌های تجاری و قبیله‌ای
        registry.put(BuildingType.BAZAAR, Bazaar::new);
        registry.put(BuildingType.TRADING_POST, TradingPost::new);

        // رفع ارور کامپایل: سازنده‌ی TribeCamp اکنون یک TribeType می‌گیرد، نه یک int.
        // در اینجا به عنوان Factory پیش‌فرض، نوع FARMER قرار داده شده است.
        // (تولید قبایل واقعی با انواع مختلف توسط TribeController انجام می‌شود).
        registry.put(BuildingType.TRIBE_CAMP, () -> new TribeCamp(TribeType.FARMER));
    }

    public static Building createBuilding(BuildingType type) {
        Supplier<Building> constructor = registry.get(type);
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown building type: " + type);
        }
        return constructor.get();
    }
}