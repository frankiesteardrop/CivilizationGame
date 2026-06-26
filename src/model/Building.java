package model;

/**
 * کلاس انتزاعی پایه برای تمامی ساختمان‌های بازی.
 * پیاده‌سازی اصول کپسوله‌سازی و مدیریت حالت سازه.
 */
public abstract class Building {
    protected int baseWorkerCapacity;
    protected int stationedWorkers;
    protected boolean isDestroyed;
    protected int consecutiveUnpaidTurns;

    public Building(int baseWorkerCapacity) {
        this.baseWorkerCapacity = baseWorkerCapacity;
        this.stationedWorkers = 0;
        this.isDestroyed = false;
        this.consecutiveUnpaidTurns = 0;
    }

    public abstract BuildingType getType();

    public ResourceType getUpkeepResource() {
        return getType().getUpkeepResource();
    }

    public int getUpkeepAmount() {
        return getType().getUpkeepCost();
    }

    public int getStationedWorkers() { return stationedWorkers; }
    public int getMaxWorkers() { return baseWorkerCapacity; }
    public boolean isDestroyed() { return isDestroyed; }

    public void addWorker() {
        if (stationedWorkers < baseWorkerCapacity) stationedWorkers++;
    }

    public void removeWorker() {
        if (stationedWorkers > 0) stationedWorkers--;
    }

    /**
     * محاسبه نرخ تولید بر اساس تعداد کارگران مستقر و نرخ پایه ساختمان
     */
    public int calculateProduction() {
        if (isDestroyed) return 0;
        return stationedWorkers * getType().getBaseProduction();
    }

    /**
     * ثبت ترن بدون پرداخت هزینه نگهداری و بررسی تخریب پس از ۳ نوبت متوالی
     */
    public void registerFailedUpkeep() {
        consecutiveUnpaidTurns++;
        if (consecutiveUnpaidTurns >= 3) {
            isDestroyed = true;
        }
    }

    public void resetFailedUpkeep() {
        consecutiveUnpaidTurns = 0;
    }
}