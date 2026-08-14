package model;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class UnitFactory {

    private static final Map<UnitType, BiFunction<Integer, Integer, Unit>> registry
            = new HashMap<>();

    static {
        registry.put(UnitType.WORKER,          Worker::new);
        registry.put(UnitType.BUILDER,         Builder::new);
        registry.put(UnitType.EXPLORER,        Explorer::new);
        registry.put(UnitType.BORDER_EXPANDER, BorderExpander::new);
        registry.put(UnitType.SWORDSMAN,       Swordsman::new);
        registry.put(UnitType.ARCHER,          Archer::new);
        registry.put(UnitType.CAVALRY,         Cavalry::new);
        // F-39: Bear به registry اضافه شد تا UnitFactory.createUnit(UnitType.BEAR, q, r)
        // بدون IllegalArgumentException کار کند
        registry.put(UnitType.BEAR,            Bear::new);
    }

    public static Unit createUnit(UnitType type, int q, int r) {
        BiFunction<Integer, Integer, Unit> constructor = registry.get(type);
        if (constructor == null) {
            throw new IllegalArgumentException("Unknown unit type: " + type);
        }
        return constructor.apply(q, r);
    }
}