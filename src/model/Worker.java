package model;

/**
 * یونیت کارگر — مسئول استقرار در ساختمان‌های تولیدی و تولید منابع.
 *
 * منطق AP طبق داک:
 * - ورود به ساختمان (station): ۱ AP مصرف می‌شود
 * - خروج از ساختمان (eject): رایگان است، AP باز نمی‌گردد
 * - در ابتدای هر Turn که Worker stationed باشد: AP = 0 (مشغول کار)
 * - در ابتدای هر Turn که Worker آزاد باشد: AP = maxAP (ریست کامل)
 */
public class Worker extends Unit {

    /** هزینه AP برای ورود به ساختمان — ثابت و قابل تنظیم */
    private static final int STATION_AP_COST = 1;

    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        super(q, r, 2, 1, 1);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    // =========================================================
    // سیستم AP — مدیریت ریست در ابتدای هر نوبت
    // =========================================================

    /**
     * ریست AP در ابتدای نوبت جدید.
     *
     * طبق داک:
     * - اگر Worker stationed باشد → AP = 0 (مشغول کار در ساختمان)
     * - اگر Worker آزاد باشد → AP = maxAP (ریست کامل)
     */
    @Override
    public void resetAP() {
        if (!isAlive) return;

        if (isStationed) {
            // Worker مستقر در ساختمان در این نوبت AP ندارد
            currentAP = 0;
        } else {
            // Worker آزاد AP کامل دریافت می‌کند
            currentAP = maxAP;
        }
    }

    // =========================================================
    // استقرار در ساختمان
    // =========================================================

    /**
     * استقرار Worker در یک ساختمان تولیدی.
     *
     * شرایط لازم:
     * - Worker از قبل stationed نباشد
     * - AP کافی برای هزینه ورود داشته باشد
     * - ساختمان خراب نباشد
     * - ساختمان ظرفیت خالی داشته باشد
     *
     * @param building ساختمانی که Worker می‌خواهد در آن مستقر شود
     * @return true اگر استقرار موفق بود
     */
    public boolean stationIn(Building building) {
        if (isStationed) return false;
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;
        if (currentAP < STATION_AP_COST) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;

        // کسر AP هزینه ورود
        consumeAP(STATION_AP_COST);

        // اطلاع به گرافیک که وضعیت یونیت تغییر کرد
        GameEventDispatcher.fireUnitStateChanged(this);

        return true;
    }

    /**
     * خروج Worker از ساختمان.
     *
     * طبق داک: خروج رایگان است و AP بازنمی‌گردد.
     * Worker بعد از خروج در همان هکس می‌ماند تا کار جدیدی به آن اختصاص داده شود.
     */
    public void eject() {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // توجه: AP برنمی‌گردد — Worker با همان AP کاهش‌یافته ادامه می‌دهد
        // اطلاع به گرافیک که وضعیت یونیت تغییر کرد
        GameEventDispatcher.fireUnitStateChanged(this);
    }

    // =========================================================
    // Getters
    // =========================================================

    public boolean isStationed() { return isStationed; }
    public Building getStationedBuilding() { return stationedBuilding; }
    public static int getStationApCost() { return STATION_AP_COST; }
}