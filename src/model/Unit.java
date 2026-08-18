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

    protected int hp;
    protected int maxHp;
    protected int attackRange;
    protected int siegeDamage;

    // اصلاح گام اول: اضافه شدن فیلد مالکیت برای رفع باگ کنترل ذهن
    protected boolean isEnemy;

    public Unit(int q, int r, UnitType type) {
        this.q = q;
        this.r = r;
        this.type = type;
        this.maxAP = type.getMaxAP();
        this.currentAP = type.getMaxAP();
        this.foodConsumption = type.getFoodConsumption();
        this.visionRadius = type.getVisionRadius();

        this.maxHp = type.getMaxHp();
        this.hp = type.getMaxHp();
        this.attackRange = type.getAttackRange();
        this.siegeDamage = type.getSiegeDamage();
        this.isAlive = true;

        // به صورت پیش‌فرض تمام نیروهای ساخته شده توسط کارخانه، نیروی خودی هستند
        this.isEnemy = false;
    }

    public void resetAP() { if (isAlive) currentAP = maxAP; }

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

    public void takeDamage(int amount) {
        if (!isAlive) return;
        this.hp -= amount;
        if (this.hp <= 0) {
            this.hp = 0;
            this.kill();
        }
        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public int getQ() { return q; }
    public int getR() { return r; }
    public UnitType getType() { return type; }
    public int getCurrentAP() { return currentAP; }
    public int getMaxAP() { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius() { return visionRadius; }

    public int getHp() { return hp; }
    public int getMaxHp() { return maxHp; }
    public int getAttackRange() { return attackRange; }
    public int getSiegeDamage() { return siegeDamage; }
    public boolean isAlive() { return isAlive; }

    // اصلاح گام اول: Getter و Setter برای کنترل مالکیت
    public boolean isEnemy() { return isEnemy; }
    public void setEnemy(boolean enemy) { this.isEnemy = enemy; }

    public void kill() {
        if (this.isAlive) {
            this.isAlive = false;
            GameEventDispatcher.fireUnitKilled(this);
        }
    }
}