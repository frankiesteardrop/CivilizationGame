package model;

public abstract class Building {
    protected int baseWorkerCapacity;
    protected int stationedWorkers;
    protected boolean isDestroyed;
    protected int consecutiveUnpaidTurns;

    // المان‌های جدید برای سیستم جنگ فاز دوم
    protected int hp;
    protected int maxHp;
    protected int defense;

    public Building(int baseWorkerCapacity) {
        this.baseWorkerCapacity = baseWorkerCapacity;
        this.stationedWorkers = 0;
        this.isDestroyed = false;
        this.consecutiveUnpaidTurns = 0;
        this.maxHp = 100; // مقدار پیش‌فرض
        this.hp = 100;
        this.defense = 0;
    }

    public abstract BuildingType getType();

    public ResourceType getUpkeepResource() { return getType().getUpkeepResource(); }
    public int getUpkeepAmount() { return getType().getUpkeepCost(); }
    public int getStationedWorkers() { return stationedWorkers; }
    public int getMaxWorkers() { return baseWorkerCapacity; }
    public boolean isDestroyed() { return isDestroyed; }
    public int getVisionRadius() { return getType().getVisionRadius(); }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getDefense() { return defense; }

    protected void setMaxHp(int maxHp) { this.maxHp = maxHp; }
    protected void setDefense(int defense) { this.defense = defense; }

    public void heal(int amount) {
        this.hp = Math.min(this.maxHp, this.hp + amount);
    }

    public void takeDamage(int amount) {
        this.hp -= amount;
        if (this.hp <= 0) {
            this.hp = 0;
            this.isDestroyed = true;
        }
    }

    public void addWorker() {
        if (stationedWorkers < baseWorkerCapacity) stationedWorkers++;
    }

    public void removeWorker() {
        if (stationedWorkers > 0) stationedWorkers--;
    }

    public int calculateProduction(TownHall townHall) {
        if (isDestroyed) return 0;
        return stationedWorkers * getType().getBaseProduction();
    }

    public void registerFailedUpkeep() {
        consecutiveUnpaidTurns++;
        if (consecutiveUnpaidTurns >= GameConfig.BUILDING_UNPAID_TURNS_TO_DESTROY) {
            isDestroyed = true;
        }
    }

    public void resetFailedUpkeep() {
        consecutiveUnpaidTurns = 0;
    }
}