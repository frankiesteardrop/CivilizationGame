package model;

public enum UnitType {
    WORKER(2, 1, 1, 1, 0, 0),
    BUILDER(4, 2, 2, 1, 0, 0),
    EXPLORER(6, 2, 5, 1, 0, 0),
    BORDER_EXPANDER(5, 2, 2, 1, 0, 0),
    SWORDSMAN(2, 2, 2, 1, 1, 10),
    ARCHER(2, 2, 3, 1, 2, 6),
    CAVALRY(4, 3, 4, 2, 1, 9),
    CATAPULT(2, 2, 2, 2, 2, 20),
    BEAR(2, 0, 1, 120, 1, 35);

    private final int maxAP;
    private final int foodConsumption;
    private final int visionRadius;
    private final int maxHp;
    private final int attackRange;
    private final int baseDamage;

    UnitType(int maxAP, int foodConsumption, int visionRadius,
             int maxHp, int attackRange, int baseDamage) {
        this.maxAP           = maxAP;
        this.foodConsumption = foodConsumption;
        this.visionRadius    = visionRadius;
        this.maxHp           = maxHp;
        this.attackRange     = attackRange;
        this.baseDamage      = baseDamage;
    }

    public int getMaxAP()           { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius()    { return visionRadius; }
    public int getMaxHp()           { return maxHp; }
    public int getAttackRange()     { return attackRange; }
    public int getBaseDamage()      { return baseDamage; }
    public int getSiegeDamage()     { return baseDamage; }
}