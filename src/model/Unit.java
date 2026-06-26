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
        this.currentAP = maxAP;
        this.foodConsumption = foodConsumption;
        this.visionRadius = visionRadius;
        this.isAlive = true;
    }

    public void resetAP() {
        if (isAlive) {
            currentAP = maxAP;
        }
    }

    public boolean consumeAP(int amount) {
        if (currentAP >= amount) {
            currentAP -= amount;
            return true;
        }
        return false;
    }

    public void moveTo(int targetQ, int targetR, int cost) {
        if (consumeAP(cost)) {
            int oldQ = this.q;
            int oldR = this.r;
            this.q = targetQ;
            this.r = targetR;
            GameEventDispatcher.fireUnitMoved(this, oldQ, oldR, targetQ, targetR);
        }
    }

    public int getQ() { return q; }
    public int getR() { return r; }
    public int getCurrentAP() { return currentAP; }
    public int getMaxAP() { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius() { return visionRadius; }
    public boolean isAlive() { return isAlive; }

    public void kill() {
        if (this.isAlive) {
            this.isAlive = false;
            GameEventDispatcher.fireUnitKilled(this);
        }
    }
}