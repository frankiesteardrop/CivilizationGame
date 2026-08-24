package model;

public enum TerrainType {
    PLAINS(1),
    FOREST(2),
    // اصلاح گام پنجم: هزینه حرکت در کوهستان دقیقاً طبق داک فاز اول به 4 تغییر یافت
    MOUNTAIN(4),
    MEADOW(1),
    SEA(9999),            // غیرقابل عبور بدون تکنولوژی دریانوردی
    MOUNTAIN_RANGE(9999); // کاملاً غیرقابل عبور مطلق

    private final int movementCost;

    TerrainType(int movementCost) {
        this.movementCost = movementCost;
    }

    public int getMovementCost() {
        return movementCost;
    }
}