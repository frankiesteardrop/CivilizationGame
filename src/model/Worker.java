package model;

public class Worker extends Unit {
    private boolean isStationed;
    private Building stationedBuilding;
    private int lockedAP; // متغیر حیاتی برای ذخیره AP هنگام استقرار

    public Worker(int q, int r) {
        super(q, r, 2, 1, 1);
        this.isStationed = false;
        this.stationedBuilding = null;
        this.lockedAP = 0;
    }

    public boolean isStationed() { return isStationed; }

    @Override
    public void resetAP() {
        if (isAlive) {
            if (isStationed) {
                // اگر کارگر در شروع ترن جدید مستقر است، AP او پر و مستقیماً قفل می‌شود
                lockedAP = maxAP;
                currentAP = 0;
            } else {
                currentAP = maxAP;
            }
        }
    }

    public boolean stationIn(Building building) {
        if (isStationed || currentAP < 1 || building.isDestroyed()) return false;

        if (building.getStationedWorkers() < building.getMaxWorkers()) {
            building.addWorker();
            this.isStationed = true;
            this.stationedBuilding = building;

            // قفل کردن کل AP فعلی به جای کم کردن ۱ واحد
            this.lockedAP = this.currentAP;
            this.currentAP = 0;

            return true;
        }
        return false;
    }

    public void eject() {
        if (isStationed && stationedBuilding != null) {
            stationedBuilding.removeWorker();
            this.isStationed = false;
            this.stationedBuilding = null;

            // بازگرداندن دقیقاً همان AP قفل شده به کارگر در همان ترن
            this.currentAP = this.lockedAP;
            this.lockedAP = 0;
        }
    }
}