package model;

public abstract class Unit {
    protected int q;
    protected int r;
    protected UnitType type;
    protected int maxAP;
    protected int currentAP;
    protected int foodConsumption;
    protected int visionRadius;
    protected boolean isAlive;

    public Unit(int q, int r, UnitType type) {
        this.q = q;
        this.r = r;
        this.type = type;
        this.maxAP = type.getMaxAP();
        this.currentAP = type.getMaxAP();
        this.foodConsumption = type.getFoodConsumption();
        this.visionRadius = type.getVisionRadius();
        this.isAlive = true;
    }

    public void resetAP() {
        if (isAlive) {
            currentAP = maxAP;
        }
    }

    public boolean consumeAP(int amount) {
        if (amount <= 0) return false;

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
    public UnitType getType() { return type; }
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