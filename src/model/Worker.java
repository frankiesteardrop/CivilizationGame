package model;

/**
 * یونیت کارگر (Worker) در لایه Model.
 * این کلاس مسئول مدیریت استقرار کارگران در تاسیسات استخراجی و کشاورزی است.
 * بر اساس اصول SOLID، این کلاس منطق اختصاصی اکشن‌پوینت (AP) در زمان استقرار را مدیریت می‌کند.
 */
public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        super(q, r, UnitType.WORKER);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    /**
     * [گام ۱ - اصلاح باگ اکشن‌پوینت کارگر مستقر]:
     * بازنویسی رفتار تجدید AP در شروع نوبت جدید.
     * اگر کارگر درون یک ساختمان مستقر باشد، هزینه حضور و کار در کارگاه در همان بدو نوبت
     * از سقف AP او کسر می‌شود تا در صورت خروج (Eject)، نتواند با AP کامل در نقشه حرکت کند.
     */
    @Override
    public void resetAP() {
        if (!isAlive) return;

        // مرحله ۱: اجرای منطق استاندارد تجدید AP از کلاس والد
        super.resetAP();

        // مرحله ۲: کسر هزینه استقرار در صورت مشغول بودن کارگر در کارگاه
        if (isStationed) {
            consumeAP(GameConfig.WORKER_STATION_AP_COST);
        }
    }

    /**
     * استقرار کارگر در ساختمان هدف.
     *
     * @param building ساختمانی که کارگر قرار است در آن مستقر شود.
     * @return true اگر استقرار با موفقیت انجام شود، در غیر این صورت false.
     */
    public boolean stationIn(Building building) {
        if (isStationed) return false;
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;
        if (currentAP < GameConfig.WORKER_STATION_AP_COST) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;

        // کسر AP بابت عمل استقرار
        consumeAP(GameConfig.WORKER_STATION_AP_COST);

        // اطلاع به لایه View جهت به‌روزرسانی رابط کاربری
        GameEventDispatcher.fireUnitStateChanged(this);
        return true;
    }

    /**
     * خروج کارگر از ساختمان (Eject).
     * طبق قوانین پروژه، خروج از کارگاه هیچ مقدار AP ای را به کارگر بازنمی‌گرداند.
     */
    public void eject() {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // اطلاع به لایه View جهت به‌روزرسانی رابط کاربری
        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public boolean isStationed() {
        return isStationed;
    }

    public Building getStationedBuilding() {
        return stationedBuilding;
    }

    public static int getStationApCost() {
        return GameConfig.WORKER_STATION_AP_COST;
    }
}