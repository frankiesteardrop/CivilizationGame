package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;

    public Worker(int q, int r) {
        super(q, r, 2, 1, 1);
        this.isStationed = false;
        this.stationedBuilding = null;
    }

    public boolean isStationed() { return isStationed; }
    public Building getStationedBuilding() { return stationedBuilding; }

    /**
     * منطق resetAP برای Worker:
     * - اگر مستقر است: AP به maxAP ریست می‌شود ولی چون کارگر مشغول کار در سازه است،
     *   بلافاصله به صفر تنظیم می‌شود تا نتواند اقدامی انجام دهد.
     * - اگر مستقر نیست: رفتار عادی مثل تمام یونیت‌ها.
     */
    @Override
    public void resetAP() {
        if (!isAlive) return;

        if (isStationed) {
            // Worker مستقر در طول کار AP قابل استفاده ندارد
            currentAP = 0;
        } else {
            currentAP = maxAP;
        }
    }

    /**
     * استقرار کارگر در سازه.
     * طبق داک: هزینه ۱ AP کسر می‌شود (نه کل AP).
     * اگر AP کافی نباشد یا سازه پر باشد یا تخریب شده باشد، عملیات انجام نمی‌شود.
     */
    public boolean stationIn(Building building) {
        if (isStationed) return false;
        if (currentAP < 1) return false;
        if (building == null || building.isDestroyed()) return false;
        if (building.getStationedWorkers() >= building.getMaxWorkers()) return false;

        building.addWorker();
        this.isStationed = true;
        this.stationedBuilding = building;

        // طبق داک: فقط ۱ واحد AP هزینه استقرار دارد
        consumeAP(1);

        return true;
    }

    /**
     * خروج کارگر از سازه.
     * طبق داک: خروج از ساختمان AP رو بازنمی‌گردونه.
     * AP همان مقدار کاهش‌یافته باقی می‌ماند.
     */
    public void eject() {
        if (!isStationed || stationedBuilding == null) return;

        stationedBuilding.removeWorker();
        this.isStationed = false;
        this.stationedBuilding = null;

        // طبق داک: AP بازنمی‌گردد — currentAP همان مقدار فعلی (صفر) باقی می‌ماند
        // هیچ تغییری در currentAP ایجاد نمی‌شود
    }
}