package model;

public abstract class Unit {
    protected int q;
    protected int r;
    protected int maxAP;
    protected int currentAP;
    protected int foodConsumption;
    protected int visionRadius;
    protected boolean isAlive;

    public Unit(int q, int r, int maxAP, int foodConsumption, int visionRadius) {
        this.q = q;
        this.r = r;
        this.maxAP = maxAP;
        this.currentAP = maxAP; // در ابتدا یونیت فول AP است
        this.foodConsumption = foodConsumption;
        this.visionRadius = visionRadius;
        this.isAlive = true;
    }

    // بازگردانی AP در شروع هر نوبت جدید
    public void resetAP() {
        if (isAlive) {
            currentAP = maxAP;
        }
    }

    // متد مصرف AP برای حرکت یا انجام کار
    public boolean consumeAP(int amount) {
        if (currentAP >= amount) {
            currentAP -= amount;
            return true;
        }
        return false;
    }

    // متد حرکت یونیت در نقشه
    public void moveTo(int targetQ, int targetR, int cost) {
        if (consumeAP(cost)) {
            this.q = targetQ;
            this.r = targetR;
        }
    }

    // Getters
    public int getQ() { return q; }
    public int getR() { return r; }
    public int getCurrentAP() { return currentAP; }
    public int getMaxAP() { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius() { return visionRadius; }
    public boolean isAlive() { return isAlive; }

    public void kill() { this.isAlive = false; }
}