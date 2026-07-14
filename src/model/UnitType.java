package model;

public enum UnitType {
    WORKER(2, 1, 1),
    BUILDER(4, 2, 2),
    EXPLORER(6, 2, 2), // شعاع دید از ۴ به ۲ کاهش یافت تا فاز اکتشاف منطقی‌تر شود
    BORDER_EXPANDER(5, 2, 2);

    private final int maxAP;
    private final int foodConsumption;
    private final int visionRadius;

    UnitType(int maxAP, int foodConsumption, int visionRadius) {
        this.maxAP = maxAP;
        this.foodConsumption = foodConsumption;
        this.visionRadius = visionRadius;
    }

    public int getMaxAP() { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius() { return visionRadius; }
}