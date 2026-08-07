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
        registry.put(BuildingType.BAZAAR, Bazaar::new);
        registry.put(BuildingType.TRADING_POST, TradingPost::new);
        registry.put(BuildingType.TRIBE_CAMP, () -> new TribeCamp(50));
    }

    public static Building createBuilding(BuildingType type) {
        Supplier<Building> constructor = registry.get(type);
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown building type: " + type);
        }
        return constructor.get();
    }
}