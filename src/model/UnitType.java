package model;

public enum UnitType {
    WORKER(2, 1, 1, 1, 0, 0),
    BUILDER(4, 2, 2, 1, 0, 0),

    /**
     * N4: افزایش visionRadius Explorer از ۴ به ۵.
     *
     * دلیل: حداقل فاصله کمپ قبایل از TH، ۶-۷ hex است (طبق spec).
     * با vision=4، بازیکن باید Explorer را تقریباً تا کمپ ببرد تا قبیله کشف شود.
     * با vision=5، Explorer می‌تواند از فاصله ۵ hex قبیله را کشف کند — یعنی
     * کمی قبل از رسیدن به کمپ اطلاع می‌یابد که قبیله‌ای در آنجا است.
     * این balance بهتری ایجاد می‌کند: Explorer هنوز نیاز به جابجایی دارد،
     * اما ارزش واقعی‌تری برای کشف دارد.
     */
    EXPLORER(6, 2, 5, 1, 0, 0),

    BORDER_EXPANDER(5, 2, 2, 1, 0, 0),

    // ارتش (AP, Food, Vision, MaxHp, AttackRange, SiegeDamage)
    SWORDSMAN(2, 2, 2, 1, 1, 10),
    ARCHER(2, 2, 3, 1, 2, 6),
    CAVALRY(4, 3, 4, 2, 1, 8),

    // خرس به عنوان بلای طبیعی
    BEAR(2, 0, 1, 120, 1, 35);

    private final int maxAP;
    private final int foodConsumption;
    private final int visionRadius;
    private final int maxHp;
    private final int attackRange;
    private final int siegeDamage;

    UnitType(int maxAP, int foodConsumption, int visionRadius,
             int maxHp, int attackRange, int siegeDamage) {
        this.maxAP          = maxAP;
        this.foodConsumption = foodConsumption;
        this.visionRadius   = visionRadius;
        this.maxHp          = maxHp;
        this.attackRange    = attackRange;
        this.siegeDamage    = siegeDamage;
    }

    public int getMaxAP()           { return maxAP; }
    public int getFoodConsumption() { return foodConsumption; }
    public int getVisionRadius()    { return visionRadius; }
    public int getMaxHp()           { return maxHp; }
    public int getAttackRange()     { return attackRange; }
    public int getSiegeDamage()     { return siegeDamage; }
}