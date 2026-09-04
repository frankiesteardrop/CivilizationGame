package model;

import java.util.UUID;

public abstract class Unit {
    protected String id;
    protected long createdAt;
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

    protected boolean isEnemy;

    protected Tribe ownerTribe;
    protected String ownerId;

    // فیلدهای کنترلی آیتم‌های مصرفی (گام ۵)
    protected boolean hasUsedItemThisTurn;
    protected int temporaryCombatDiceBonus;
    protected int temporarySiegeBonus;

    public Unit(int q, int r, UnitType type) {
        this.id        = UUID.randomUUID().toString();
        this.createdAt = System.currentTimeMillis();
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

        this.isEnemy = false;
        this.ownerTribe = null;
        this.ownerId = null;

        this.hasUsedItemThisTurn = false;
        this.temporaryCombatDiceBonus = 0;
        this.temporarySiegeBonus = 0;
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

    // متدهای مدیریت آیتم‌ها
    public boolean hasUsedItemThisTurn() { return hasUsedItemThisTurn; }
    public void setUsedItemThisTurn(boolean used) { this.hasUsedItemThisTurn = used; }

    public void addTemporaryAP(int amount) {
        this.currentAP += amount;
        GameEventDispatcher.fireUnitStateChanged(this);
    }

    public void setTemporaryCombatDiceBonus(int bonus) { this.temporaryCombatDiceBonus = bonus; }
    public int getTemporaryCombatDiceBonus() { return temporaryCombatDiceBonus; }

    public void setTemporarySiegeBonus(int bonus) { this.temporarySiegeBonus = bonus; }
    public int getTemporarySiegeBonus() { return temporarySiegeBonus; }

    public void resetItemBuffs() {
        this.hasUsedItemThisTurn = false;
        this.temporaryCombatDiceBonus = 0;
        this.temporarySiegeBonus = 0;
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

    // اعمال باف آیتم مبارزه روی آسیب به سازه
    public int getSiegeDamage() { return siegeDamage + temporarySiegeBonus; }

    public boolean isAlive() { return isAlive; }

    public boolean isEnemy() { return isEnemy; }
    public void setEnemy(boolean enemy) { this.isEnemy = enemy; }

    public Tribe getOwnerTribe() { return ownerTribe; }
    public void setOwnerTribe(Tribe ownerTribe) { this.ownerTribe = ownerTribe; }

    public String getOwnerId() { return ownerId; }
    public String getId()        { return id; }
    public long   getCreatedAt() { return createdAt; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public void kill() {
        if (this.isAlive) {
            this.isAlive = false;
            GameEventDispatcher.fireUnitKilled(this);
        }
    }
}