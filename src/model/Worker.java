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

    /**
     * استقرار اصولی کارگر داخل ساختمان با مصرف AP و بررسی سقف مجاز ساختمان
     */
    public boolean stationIn(Building building) {
        if (isStationed || currentAP < 1 || building.isDestroyed()) return false;

        if (building.getStationedWorkers() < building.getMaxWorkers()) {
            building.addWorker();
            this.isStationed = true;
            this.stationedBuilding = building;

            // رفع باگ شماره ۴: فقط ۱ واحد AP برای استقرار مصرف می‌شود، نه کل آن
            consumeAP(1);

            return true;
        }
        return false;
    }

    /**
     * اخراج یا خروج کارگر از ساختمان بدون بازگرداندن AP تا پایان ترن
     */
    public void eject() {
        if (isStationed && stationedBuilding != null) {
            stationedBuilding.removeWorker();
            this.isStationed = false;
            this.stationedBuilding = null;
        }
    }
}